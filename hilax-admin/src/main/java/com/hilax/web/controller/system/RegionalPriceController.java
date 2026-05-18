package com.hilax.web.controller.system;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hilax.common.config.HilaxConfig;
import com.hilax.common.core.controller.BaseController;
import com.hilax.common.core.domain.AjaxResult;

/**
 * 速卖通 POP 区域化定价计算
 */
@RestController
@RequestMapping("/system/regional-price")
public class RegionalPriceController extends BaseController
{
    private static final String REGION_SHEET = "区域化定价";

    private static final String TAX_RATE_FILE = "tax-rates.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> LOGISTICS_SHEETS = new HashMap<>();

    static
    {
        LOGISTICS_SHEETS.put("standard", "无忧物流-标准");
        LOGISTICS_SHEETS.put("special", "无忧物流-特货标准");
        LOGISTICS_SHEETS.put("largeLand", "无忧物流-陆运大件专线");
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @PostMapping("/calculate")
    public AjaxResult calculate(@RequestBody RegionalPriceRequest request) throws Exception
    {
        Path regionPath = templatePath("区域化定价.xlsx");
        Path freightPath = templatePath("文件1.xlsx");
        if (!Files.exists(regionPath) || !Files.exists(freightPath))
        {
            return error("还没有保存默认模板，请先在页面上传一次区域化定价.xlsx和文件1.xlsx");
        }
        try (InputStream regionInput = Files.newInputStream(regionPath);
             InputStream freightInput = Files.newInputStream(freightPath))
        {
            return calculate(regionInput, freightInput, request);
        }
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @PostMapping(value = "/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadTemplates(@RequestParam(value = "regionFile", required = false) MultipartFile regionFile,
                                      @RequestParam(value = "freightFile", required = false) MultipartFile freightFile) throws Exception
    {
        if (regionFile == null && freightFile == null)
        {
            return error("请选择要更新的模板文件");
        }
        if (regionFile != null && !regionFile.isEmpty())
        {
            saveTemplate(regionFile, "区域化定价.xlsx");
        }
        if (freightFile != null && !freightFile.isEmpty())
        {
            saveTemplate(freightFile, "文件1.xlsx");
        }
        return templateStatus();
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @GetMapping("/templates")
    public AjaxResult templateStatus()
    {
        AjaxResult ajax = success();
        ajax.put("regionTemplate", templateInfo("区域化定价.xlsx"));
        ajax.put("freightTemplate", templateInfo("文件1.xlsx"));
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @GetMapping("/tax-rates")
    public AjaxResult taxRates()
    {
        AjaxResult ajax = success();
        ajax.put("rows", loadTaxRates());
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @GetMapping("/countries")
    public AjaxResult countries() throws Exception
    {
        AjaxResult ajax = success();
        ajax.put("rows", loadRegionCountries());
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:regionalPrice:list')")
    @PostMapping("/tax-rates")
    public AjaxResult saveTaxRates(@RequestBody List<TaxRateConfig> rows) throws Exception
    {
        List<TaxRateConfig> normalized = normalizeTaxRates(rows);
        Path path = templatePath(TAX_RATE_FILE);
        Files.createDirectories(path.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), normalized);
        AjaxResult ajax = success();
        ajax.put("rows", normalized);
        return ajax;
    }
    private AjaxResult calculate(InputStream regionInput, InputStream freightInput, RegionalPriceRequest request) throws Exception
    {
        if (request == null)
        {
            request = new RegionalPriceRequest();
        }
        String logisticsType = request.logisticsType == null ? "standard" : request.logisticsType;
        String sheetName = LOGISTICS_SHEETS.getOrDefault(logisticsType, LOGISTICS_SHEETS.get("standard"));
        String cargoType = "nonGeneral".equals(request.cargoType) ? "nonGeneral" : "general";
        try (Workbook regionBook = WorkbookFactory.create(regionInput);
             Workbook freightBook = WorkbookFactory.create(freightInput))
        {
            FormulaEvaluator regionEvaluator = regionBook.getCreationHelper().createFormulaEvaluator();
            Sheet regionSheet = getSheet(regionBook, REGION_SHEET);
            Sheet freightSheet = getSheet(freightBook, sheetName);

            PriceParams params = readParams(regionSheet, regionEvaluator);
            params.weightKg = valueOrDefault(request.weightKg, params.weightKg);
            params.commissionRate = valueOrDefault(request.commissionRate, params.commissionRate);
            params.costRmb = valueOrDefault(request.costRmb, params.costRmb);
            params.exchangeRate = valueOrDefault(request.exchangeRate, params.exchangeRate);
            params.discount = valueOrDefault(request.discount, params.discount);
            params.profitRate = valueOrDefault(request.profitRate, params.profitRate);

            if (params.weightKg <= 0 || params.exchangeRate <= 0 || params.discount <= 0)
            {
                return error("重量、汇率、折扣必须大于 0");
            }
            if (params.commissionRate + params.profitRate >= 1)
            {
                return error("佣金率 + 利润率必须小于 1");
            }

            List<CountryPrice> rows = new ArrayList<>();
            List<Country> countries = readCountries(regionSheet, regionEvaluator);
            List<TaxRateConfig> taxRates = loadTaxRates();
            StringBuilder header = new StringBuilder();
            StringBuilder priceLine = new StringBuilder();
            int scale = Math.max(0, Math.min(6, request.precision == null ? 2 : request.precision));

            for (Country country : countries)
            {
                FreightRate rate = findRate(freightSheet, country, params.weightKg, logisticsType, cargoType);
                CountryPrice row = buildPrice(country, rate, params, scale, findTaxRate(country, taxRates));
                rows.add(row);
                if (isOthers(country))
                {
                    continue;
                }
                if (header.length() > 0)
                {
                    header.append('\t');
                    priceLine.append('\t');
                }
                header.append(country.name);
                priceLine.append(row.priceText == null ? "" : row.priceText);
            }

            AjaxResult ajax = success();
            ajax.put("params", params);
            ajax.put("sheetName", sheetName);
            ajax.put("cargoType", cargoType);
            ajax.put("rows", rows);
            ajax.put("copyText", header.append('\n').append(priceLine).toString());
            ajax.put("warnings", rows.stream().filter(item -> item.warning != null && !item.warning.isEmpty()).count());
            return ajax;
        }
    }

    private void saveTemplate(MultipartFile file, String fileName) throws Exception
    {
        Path path = templatePath(fileName);
        Files.createDirectories(path.getParent());
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
    }

    private Map<String, Object> templateInfo(String fileName)
    {
        Path path = templatePath(fileName);
        Map<String, Object> info = new HashMap<>();
        info.put("fileName", fileName);
        info.put("exists", Files.exists(path));
        if (Files.exists(path))
        {
            try
            {
                info.put("size", Files.size(path));
                info.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                info.put("path", path.toString());
            }
            catch (Exception ignored)
            {
            }
        }
        return info;
    }

    private Path templatePath(String fileName)
    {
        return Path.of(HilaxConfig.getProfile(), "regional-price", fileName);
    }

    private List<Map<String, String>> loadRegionCountries() throws Exception
    {
        Path regionPath = templatePath("区域化定价.xlsx");
        List<Map<String, String>> rows = new ArrayList<>();
        if (!Files.exists(regionPath))
        {
            return rows;
        }
        try (InputStream regionInput = Files.newInputStream(regionPath);
             Workbook regionBook = WorkbookFactory.create(regionInput))
        {
            FormulaEvaluator regionEvaluator = regionBook.getCreationHelper().createFormulaEvaluator();
            for (Country country : readCountries(getSheet(regionBook, REGION_SHEET), regionEvaluator))
            {
                if (isOthers(country))
                {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                row.put("country", country.name);
                row.put("countryEnglish", country.englishName);
                row.put("key", countryKey(country.name, country.englishName));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<TaxRateConfig> loadTaxRates()
    {
        Path path = templatePath(TAX_RATE_FILE);
        if (Files.exists(path))
        {
            try
            {
                List<TaxRateConfig> rows = OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<List<TaxRateConfig>>() {});
                return normalizeTaxRates(rows);
            }
            catch (Exception ignored)
            {
            }
        }
        return normalizeTaxRates(defaultTaxRates());
    }

    private List<TaxRateConfig> defaultTaxRates()
    {
        List<TaxRateConfig> rows = new ArrayList<>();
        rows.add(new TaxRateConfig("哥伦比亚", "Colombia", 0.19D));
        rows.add(new TaxRateConfig("摩洛哥", "Morocco", 0.52D));
        return rows;
    }

    private List<TaxRateConfig> normalizeTaxRates(List<TaxRateConfig> rows)
    {
        List<TaxRateConfig> normalized = new ArrayList<>();
        if (rows == null)
        {
            return normalized;
        }
        for (TaxRateConfig row : rows)
        {
            if (row == null || (isBlank(row.country) && isBlank(row.countryEnglish)))
            {
                continue;
            }
            double rate = row.taxRate == null ? 0D : row.taxRate;
            if (rate > 1D)
            {
                rate = rate / 100D;
            }
            if (rate < 0D)
            {
                rate = 0D;
            }
            row.country = row.country == null ? "" : row.country.trim();
            row.countryEnglish = row.countryEnglish == null ? "" : row.countryEnglish.trim();
            row.taxRate = rate;
            normalized.add(row);
        }
        return normalized;
    }

    private double findTaxRate(Country country, List<TaxRateConfig> taxRates)
    {
        for (TaxRateConfig config : taxRates)
        {
            if (equalsIgnoreBlank(config.country, country.name) || equalsIgnoreBlank(config.countryEnglish, country.englishName))
            {
                return config.taxRate == null ? 0D : config.taxRate;
            }
        }
        return 0D;
    }

    private CountryPrice buildPrice(Country country, FreightRate rate, PriceParams params, int scale, double taxRate)
    {
        CountryPrice row = new CountryPrice();
        row.country = country.name;
        row.countryEnglish = country.englishName;
        row.code = rate == null ? "" : rate.code;
        if (rate == null)
        {
            rate = defaultRate(country, params.weightKg);
        }

        row.serviceRate = round(rate.serviceRate, 4);
        row.registerFee = round(rate.registerFee, 4);
        row.freightFee = round(rate.freightFee, 4);
        row.billingWeightKg = round(rate.billingWeightKg, 4);
        row.freightRule = rate.rule;

        double denominator = 1 - params.commissionRate - params.profitRate - taxRate;
        if (denominator <= 0)
        {
            row.taxRate = round(taxRate * 100D, 4);
            row.warning = "税率 + 佣金率 + 利润率必须小于 100%";
            return row;
        }

        double actualPrice = (params.costRmb + rate.freightFee) / denominator;
        double listPrice = actualPrice / (params.discount * 0.1D);
        double commission = actualPrice * params.commissionRate;
        double taxFee = actualPrice * taxRate;
        double profit = actualPrice - rate.freightFee - params.costRmb - commission - taxFee;

        row.actualPrice = round(actualPrice, 4);
        row.usdPrice = round(actualPrice / params.exchangeRate, 4);
        row.commission = round(commission, 4);
        row.taxRate = round(taxRate * 100D, 4);
        row.taxFee = round(taxFee, 4);
        row.profit = round(profit, 4);
        row.priceRmb = round(listPrice, scale);
        row.priceText = format(row.priceRmb, scale);
        if (rate.warning != null)
        {
            row.warning = rate.warning;
        }
        return row;
    }

    private FreightRate defaultRate(Country country, double weightKg)
    {
        FreightRate rate = new FreightRate();
        rate.country = country.name;
        rate.countryEnglish = country.englishName;
        rate.code = "";
        rate.serviceRate = isOthers(country) ? 87D : 150D;
        rate.registerFee = 20D;
        rate.billingWeightKg = weightKg;
        rate.freightFee = weightKg * rate.serviceRate + rate.registerFee;
        rate.rule = isOthers(country) ? "其他默认费率" : "未匹配默认费率";
        rate.warning = isOthers(country) ? "其他使用默认费率：87 + 20" : "未在运费模板中找到，使用默认费率：150 + 20";
        return rate;
    }

    private boolean isOthers(Country country)
    {
        return "其他".equals(country.name) || "Others".equalsIgnoreCase(country.englishName);
    }

    private PriceParams readParams(Sheet sheet, FormulaEvaluator evaluator)
    {
        PriceParams params = new PriceParams();
        params.weightKg = doubleValue(sheet, evaluator, 0, 1, 0.6D);
        params.commissionRate = doubleValue(sheet, evaluator, 0, 3, 0.08D);
        params.costRmb = doubleValue(sheet, evaluator, 0, 5, 120D);
        params.exchangeRate = doubleValue(sheet, evaluator, 0, 7, 7.1D);
        params.discount = doubleValue(sheet, evaluator, 0, 9, 6.7D);
        params.profitRate = doubleValue(sheet, evaluator, 0, 11, 0.08D);
        return params;
    }

    private List<Country> readCountries(Sheet sheet, FormulaEvaluator evaluator)
    {
        List<Country> countries = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++)
        {
            String name = textValue(sheet, evaluator, i, 2);
            String englishName = textValue(sheet, evaluator, i, 3);
            if (isBlank(name) && isBlank(englishName))
            {
                continue;
            }
            countries.add(new Country(name, englishName));
        }
        return countries;
    }

    private FreightRate findRate(Sheet sheet, Country country, double weightKg, String logisticsType, String cargoType)
    {
        List<Segment> segments = new ArrayList<>();
        int grams = (int) Math.ceil(weightKg * 1000D);

        for (int r = 0; r <= sheet.getLastRowNum(); r++)
        {
            Row row = sheet.getRow(r);
            String firstCell = stringValue(row, 0);
            if (firstCell.contains("运达国家"))
            {
                segments = buildSegments(sheet, r);
                continue;
            }

            if (!"standard".equals(logisticsType))
            {
                FreightRate normalized = normalizedRate(row, country, weightKg, grams);
                if (normalized != null)
                {
                    return normalized;
                }
            }

            if (!matchesCountry(row, country))
            {
                continue;
            }

            for (Segment segment : segments)
            {
                if (!shouldUseSegment(segment, logisticsType, cargoType))
                {
                    continue;
                }
                if (segment.matches(grams))
                {
                    FreightRate rate = rateFromSegment(row, segment, weightKg, grams);
                    if (rate != null)
                    {
                        rate.country = stringValue(row, 0);
                        rate.countryEnglish = stringValue(row, 1);
                        rate.code = stringValue(row, 2);
                        return rate;
                    }
                }
            }
        }
        return null;
    }

    private List<Segment> buildSegments(Sheet sheet, int headerRowIndex)
    {
        List<Segment> segments = new ArrayList<>();
        Row intervalRow = sheet.getRow(headerRowIndex + 1);
        Row priceRow = sheet.getRow(headerRowIndex + 2);
        for (int c = 3; c < 20; c += 2)
        {
            String interval = stringValue(intervalRow, c);
            String priceHeader = stringValue(priceRow, c);
            if (isBlank(interval) && isBlank(priceHeader))
            {
                continue;
            }
            Segment segment = parseSegment(interval);
            segment.startCol = c;
            segment.description = join(interval, priceHeader);
            segment.cargoType = resolveSegmentCargoType(sheet, headerRowIndex, c);
            segment.firstContinue = priceHeader.contains("首重") || stringValue(priceRow, c + 1).contains("续重");
            segment.roundTo100g = segment.description.contains("100g") || segment.description.contains("100G");
            segment.minBillingKg = segment.description.contains("首重2KG") || segment.description.contains("不足2kg") ? 2D : 0D;
            segments.add(segment);
        }
        return segments;
    }

    private boolean shouldUseSegment(Segment segment, String logisticsType, String cargoType)
    {
        if (!"standard".equals(logisticsType) || isBlank(segment.cargoType))
        {
            return true;
        }
        if ("large".equals(segment.cargoType))
        {
            return true;
        }
        return Objects.equals(cargoType, segment.cargoType);
    }

    private String resolveSegmentCargoType(Sheet sheet, int headerRowIndex, int colIndex)
    {
        Row sectionRow = sheet.getRow(headerRowIndex);
        for (int c = colIndex; c >= 3; c--)
        {
            String section = stringValue(sectionRow, c);
            if (isBlank(section))
            {
                continue;
            }
            if (section.contains("非普货"))
            {
                return "nonGeneral";
            }
            if (section.contains("大包"))
            {
                return "large";
            }
            if (section.contains("普货"))
            {
                return "general";
            }
            return "";
        }
        return "";
    }

    private FreightRate rateFromSegment(Row row, Segment segment, double weightKg, int grams)
    {
        Double first = numeric(row, segment.startCol);
        Double second = numeric(row, segment.startCol + 1);
        if (first == null || second == null)
        {
            return null;
        }

        FreightRate rate = new FreightRate();
        rate.rule = segment.description;
        if (segment.firstContinue)
        {
            int continuedUnits = (int) Math.ceil(Math.max(0, grams - 500) / 500D);
            rate.serviceRate = first;
            rate.registerFee = second;
            rate.billingWeightKg = weightKg;
            rate.freightFee = first + continuedUnits * second;
            rate.rule = rate.rule + "，首重+续重";
        }
        else
        {
            double billingWeight = weightKg;
            if (segment.roundTo100g)
            {
                billingWeight = Math.ceil(grams / 100D) * 100D / 1000D;
            }
            billingWeight = Math.max(billingWeight, segment.minBillingKg);
            rate.serviceRate = first;
            rate.registerFee = second;
            rate.billingWeightKg = billingWeight;
            rate.freightFee = billingWeight * first + second;
        }
        return rate;
    }

    private FreightRate normalizedRate(Row row, Country country, double weightKg, int grams)
    {
        if (!matchesCountry(row, country))
        {
            return null;
        }
        Double start = numeric(row, 10);
        Double end = numeric(row, 11);
        Double service = numeric(row, 12);
        Double register = numeric(row, 13);
        Double step = numeric(row, 14);
        if (start == null || end == null || service == null || register == null)
        {
            return null;
        }
        if (grams < start || grams > end)
        {
            return null;
        }

        double billingWeight = weightKg;
        if (step != null && step > 1)
        {
            billingWeight = Math.ceil(grams / step) * step / 1000D;
        }

        FreightRate rate = new FreightRate();
        rate.country = stringValue(row, 0);
        rate.countryEnglish = stringValue(row, 1);
        rate.code = stringValue(row, 2);
        rate.serviceRate = service;
        rate.registerFee = register;
        rate.billingWeightKg = billingWeight;
        rate.freightFee = billingWeight * service + register;
        rate.rule = format(start, 0) + "-" + format(end, 0) + "g";
        return rate;
    }

    private Segment parseSegment(String text)
    {
        Segment segment = new Segment();
        segment.minGram = 0;
        segment.maxGram = Integer.MAX_VALUE;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-~～至]\\s*(\\d+(?:\\.\\d+)?)\\s*(g|G|克|kg|KG)?").matcher(text);
        if (matcher.find())
        {
            double min = Double.parseDouble(matcher.group(1));
            double max = Double.parseDouble(matcher.group(2));
            String unit = matcher.group(3) == null ? "g" : matcher.group(3);
            if (unit.toLowerCase(Locale.ROOT).contains("kg"))
            {
                min *= 1000;
                max *= 1000;
            }
            segment.minGram = (int) Math.floor(min);
            segment.maxGram = (int) Math.floor(max);
        }
        return segment;
    }

    private boolean matchesCountry(Row row, Country country)
    {
        if (row == null)
        {
            return false;
        }
        String cn = stringValue(row, 0);
        String en = stringValue(row, 1);
        return equalsIgnoreBlank(cn, country.name) || equalsIgnoreBlank(en, country.englishName);
    }

    private boolean equalsIgnoreBlank(String left, String right)
    {
        return !isBlank(left) && !isBlank(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private String countryKey(String country, String countryEnglish)
    {
        return (country == null ? "" : country.trim()) + "|" + (countryEnglish == null ? "" : countryEnglish.trim());
    }

    private Sheet getSheet(Workbook workbook, String sheetName)
    {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null)
        {
            throw new IllegalArgumentException("Excel 中找不到工作表：" + sheetName);
        }
        return sheet;
    }

    private String textValue(Sheet sheet, FormulaEvaluator evaluator, int rowIndex, int colIndex)
    {
        Row row = sheet.getRow(rowIndex);
        if (row == null)
        {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null)
        {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA)
        {
            try
            {
                return Objects.toString(evaluator.evaluate(cell).formatAsString(), "").replace("\"", "");
            }
            catch (Exception ignored)
            {
                return cell.getCellFormula();
            }
        }
        return cell.toString().trim();
    }

    private double doubleValue(Sheet sheet, FormulaEvaluator evaluator, int rowIndex, int colIndex, double defaultValue)
    {
        Row row = sheet.getRow(rowIndex);
        if (row == null)
        {
            return defaultValue;
        }
        Double value = numeric(row, colIndex);
        if (value != null)
        {
            return value;
        }
        String text = textValue(sheet, evaluator, rowIndex, colIndex);
        try
        {
            return Double.parseDouble(text);
        }
        catch (Exception ignored)
        {
            return defaultValue;
        }
    }

    private Double numeric(Row row, int colIndex)
    {
        if (row == null)
        {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null)
        {
            return null;
        }
        try
        {
            if (cell.getCellType() == CellType.NUMERIC)
            {
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC)
            {
                return cell.getNumericCellValue();
            }
            String text = cell.toString().trim();
            if (isBlank(text) || text.contains("暂无"))
            {
                return null;
            }
            return Double.parseDouble(text);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private String stringValue(Row row, int colIndex)
    {
        if (row == null)
        {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        return cell == null ? "" : cell.toString().trim();
    }

    private String join(String left, String right)
    {
        if (isBlank(left))
        {
            return right;
        }
        if (isBlank(right))
        {
            return left;
        }
        return left + " / " + right;
    }

    private boolean isBlank(String text)
    {
        return text == null || text.trim().isEmpty();
    }

    private double valueOrDefault(Double value, double defaultValue)
    {
        return value == null ? defaultValue : value;
    }

    private Double round(double value, int scale)
    {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String format(double value, int scale)
    {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static class PriceParams
    {
        public double weightKg;
        public double commissionRate;
        public double costRmb;
        public double exchangeRate;
        public double discount;
        public double profitRate;
    }

    public static class RegionalPriceRequest
    {
        public String logisticsType;
        public String cargoType;
        public Double weightKg;
        public Double commissionRate;
        public Double costRmb;
        public Double exchangeRate;
        public Double discount;
        public Double profitRate;
        public Integer precision;
    }

    private static class Country
    {
        private final String name;
        private final String englishName;

        private Country(String name, String englishName)
        {
            this.name = name;
            this.englishName = englishName;
        }
    }

    private static class Segment
    {
        private int minGram;
        private int maxGram;
        private int startCol;
        private boolean firstContinue;
        private boolean roundTo100g;
        private double minBillingKg;
        private String description;
        private String cargoType;

        private boolean matches(int grams)
        {
            return grams >= minGram && grams <= maxGram;
        }
    }

    private static class FreightRate
    {
        public String country;
        public String countryEnglish;
        public String code;
        public double serviceRate;
        public double registerFee;
        public double billingWeightKg;
        public double freightFee;
        public String rule;
        public String warning;
    }

    public static class TaxRateConfig
    {
        public String country;
        public String countryEnglish;
        public Double taxRate;

        public TaxRateConfig()
        {
        }

        public TaxRateConfig(String country, String countryEnglish, Double taxRate)
        {
            this.country = country;
            this.countryEnglish = countryEnglish;
            this.taxRate = taxRate;
        }
    }

    public static class CountryPrice
    {
        public String country;
        public String countryEnglish;
        public String code;
        public Double serviceRate;
        public Double registerFee;
        public Double billingWeightKg;
        public Double freightFee;
        public Double actualPrice;
        public Double usdPrice;
        public Double commission;
        public Double taxRate;
        public Double taxFee;
        public Double profit;
        public Double priceRmb;
        public String priceText;
        public String freightRule;
        public String warning;
    }
}
