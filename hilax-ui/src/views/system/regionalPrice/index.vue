<template>
  <div class="app-container regional-price-page">
    <el-form ref="form" :model="form" label-width="86px" size="small" class="price-form">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="物流方式">
            <el-select v-model="form.logisticsType" style="width: 100%">
              <el-option label="无忧物流-标准" value="standard" />
              <el-option label="无忧物流-特货标准" value="special" />
              <el-option label="无忧物流-陆运大件专线" value="largeLand" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="form.logisticsType === 'standard'" :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="货物类型">
            <el-radio-group v-model="form.cargoType" size="small" class="cargo-type-group">
              <el-radio-button label="general">普货</el-radio-button>
              <el-radio-button label="nonGeneral">非普货</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="重量/kg">
            <el-input-number v-model="form.weightKg" :precision="3" :step="0.1" :min="0.001" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="成本/元">
            <el-input-number v-model="form.costRmb" :precision="2" :step="1" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="佣金率%">
            <el-input-number v-model="form.commissionRate" :precision="2" :step="0.1" :min="0" :max="99" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="利润率%">
            <el-input-number v-model="form.profitRate" :precision="2" :step="0.1" :min="0" :max="99" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="汇率">
            <el-input-number v-model="form.exchangeRate" :precision="4" :step="0.1" :min="0.0001" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="折扣">
            <el-input-number v-model="form.discount" :precision="2" :step="0.1" :min="0.01" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8" :xl="6">
          <el-form-item label="价格位数">
            <el-input-number v-model="form.precision" :step="1" :min="0" :max="6" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div class="template-toggle">
      <el-button icon="el-icon-upload2" size="mini" @click="showTemplateUpdate = !showTemplateUpdate">
        {{ showTemplateUpdate ? '收起模板更新' : '运费模板更新' }}
      </el-button>
    </div>

    <el-alert
      v-if="showTemplateUpdate"
      :closable="false"
      show-icon
      type="info"
      class="template-alert"
    >
      <template slot="title">
        默认模板：
        区域化定价.xlsx {{ templateStatus.regionTemplate.exists ? '已保存' : '未保存' }}；
        文件1.xlsx {{ templateStatus.freightTemplate.exists ? '已保存' : '未保存' }}
      </template>
    </el-alert>

    <el-row v-if="showTemplateUpdate" :gutter="16" class="upload-row">
      <el-col :xs="24" :md="12">
        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :file-list="regionFiles"
          :on-change="handleRegionChange"
          :on-remove="handleRegionRemove"
        >
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">需要更新时上传区域化定价.xlsx</div>
        </el-upload>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :file-list="freightFiles"
          :on-change="handleFreightChange"
          :on-remove="handleFreightRemove"
        >
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">需要更新时上传文件1.xlsx 运费模板</div>
        </el-upload>
      </el-col>
    </el-row>

    <div class="action-bar">
      <el-button v-if="showTemplateUpdate" icon="el-icon-upload2" :loading="uploading" :disabled="!regionFile && !freightFile" @click="handleUploadTemplates">更新默认模板</el-button>
      <el-button type="primary" icon="el-icon-magic-stick" :loading="loading" @click="handleCalculate">计算区域化价格</el-button>
      <el-button icon="el-icon-s-tools" @click="openTaxRateDialog">区域定价国家默认税率</el-button>
      <el-button icon="el-icon-document-copy" :disabled="!copyText" @click="copyResult">复制店小秘表格</el-button>
    </div>

    <el-alert
      v-if="result.rows.length"
      :type="result.warnings ? 'warning' : 'success'"
      :closable="false"
      show-icon
      class="result-alert"
    >
      <template slot="title">
        已按 {{ result.sheetName }} 计算 {{ result.rows.length }} 个国家/地区
        <span v-if="result.warnings">，其中 {{ result.warnings }} 个没有匹配到运费</span>
      </template>
    </el-alert>

    <el-input
      v-if="copyText"
      v-model="copyText"
      type="textarea"
      :rows="4"
      readonly
      class="copy-box"
    />

    <el-table v-if="result.rows.length" v-loading="loading" :data="result.rows" border height="520">
      <el-table-column label="国家/地区" prop="country" width="130" fixed />
      <el-table-column label="英文名" prop="countryEnglish" width="170" />
      <el-table-column label="Code" prop="code" width="80" />
      <el-table-column label="报价/RMB" prop="priceText" width="110" align="right" fixed="right" />
      <el-table-column label="运费/RMB" prop="freightFee" width="110" align="right" />
      <el-table-column label="计费重/kg" prop="billingWeightKg" width="110" align="right" />
      <el-table-column label="基础费率" prop="serviceRate" width="110" align="right" />
      <el-table-column label="挂号/续重" prop="registerFee" width="110" align="right" />
      <el-table-column label="实际售价" prop="actualPrice" width="110" align="right" />
      <el-table-column label="美元售价" prop="usdPrice" width="110" align="right" />
      <el-table-column label="税率%" prop="taxRate" width="90" align="right" />
      <el-table-column label="税费/RMB" prop="taxFee" width="110" align="right" />
      <el-table-column label="利润/RMB" prop="profit" width="110" align="right" />
      <el-table-column label="计费规则" prop="freightRule" min-width="240" show-overflow-tooltip />
      <el-table-column label="提示" prop="warning" min-width="180" show-overflow-tooltip />
    </el-table>
    <el-dialog title="区域定价国家默认税率" :visible.sync="taxRateDialogVisible" width="760px" append-to-body>
      <el-table :data="taxRateRows" border>
        <el-table-column label="国家/地区" min-width="260">
          <template slot-scope="scope">
            <el-select
              v-model="scope.row.countryKey"
              filterable
              size="small"
              style="width: 100%"
              placeholder="请选择国家/地区"
              no-data-text="请先确认区域化定价模板已保存"
              @change="handleTaxCountryChange(scope.row)"
            >
              <el-option
                v-for="item in countryOptions"
                :key="item.key"
                :label="item.countryEnglish ? item.country + ' / ' + item.countryEnglish : item.country"
                :value="item.key"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="英文名" min-width="180">
          <template slot-scope="scope">
            <span>{{ scope.row.countryEnglish || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="税率%" width="160">
          <template slot-scope="scope">
            <el-input-number v-model="scope.row.taxPercent" :precision="2" :step="1" :min="0" :max="99" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template slot-scope="scope">
            <el-button type="text" class="danger-link" @click="removeTaxRate(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="tax-rate-actions">
        <el-button icon="el-icon-plus" size="small" @click="addTaxRate">新增国家</el-button>
      </div>
      <span slot="footer">
        <el-button @click="taxRateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTaxRates" @click="saveTaxRates">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { calculateRegionalPrice, getRegionalPriceCountries, getRegionalPriceTaxRates, getRegionalPriceTemplates, saveRegionalPriceTaxRates, uploadRegionalPriceTemplates } from '@/api/system/regionalPrice'

export default {
  name: 'RegionalPrice',
  data() {
    return {
      loading: false,
      uploading: false,
      regionFile: null,
      freightFile: null,
      showTemplateUpdate: false,
      taxRateDialogVisible: false,
      savingTaxRates: false,
      regionFiles: [],
      freightFiles: [],
      taxRateRows: [],
      countryOptions: [],
      copyText: '',
      result: {
        rows: [],
        warnings: 0,
        sheetName: ''
      },
      templateStatus: {
        regionTemplate: { exists: false },
        freightTemplate: { exists: false }
      },
      form: {
        logisticsType: 'standard',
        cargoType: 'general',
        weightKg: 0.6,
        commissionRate: 8,
        costRmb: 120,
        exchangeRate: 7.1,
        discount: 6.7,
        profitRate: 8,
        precision: 2
      }
    }
  },
  created() {
    this.loadTemplateStatus()
    this.loadCountryOptions().then(() => this.loadTaxRates())
  },
  methods: {
    loadTemplateStatus() {
      getRegionalPriceTemplates().then(res => {
        this.templateStatus = {
          regionTemplate: res.regionTemplate || { exists: false },
          freightTemplate: res.freightTemplate || { exists: false }
        }
      })
    },
    countryKey(country, countryEnglish) {
      return `${(country || '').trim()}|${(countryEnglish || '').trim()}`
    },
    normalizeTaxRateRows(rows) {
      return (rows || []).map(item => {
        const key = this.countryKey(item.country, item.countryEnglish)
        const option = this.countryOptions.find(country => country.key === key) ||
          this.countryOptions.find(country => country.country === item.country || country.countryEnglish === item.countryEnglish)
        return {
          countryKey: option ? option.key : '',
          country: option ? option.country : (item.country || ''),
          countryEnglish: option ? option.countryEnglish : (item.countryEnglish || ''),
          taxPercent: this.toPercent(item.taxRate)
        }
      })
    },
    loadCountryOptions() {
      return getRegionalPriceCountries().then(res => {
        this.countryOptions = res.rows || []
        this.taxRateRows = this.normalizeTaxRateRows(this.taxRateRows.map(item => ({
          country: item.country,
          countryEnglish: item.countryEnglish,
          taxRate: Number(item.taxPercent || 0) / 100
        })))
      })
    },
    loadTaxRates() {
      return getRegionalPriceTaxRates().then(res => {
        this.taxRateRows = this.normalizeTaxRateRows(res.rows || [])
      })
    },
    openTaxRateDialog() {
      this.loadCountryOptions().then(() => this.loadTaxRates()).then(() => {
        this.taxRateDialogVisible = true
      })
    },
    addTaxRate() {
      this.taxRateRows.push({
        countryKey: '',
        country: '',
        countryEnglish: '',
        taxPercent: 0
      })
    },
    handleTaxCountryChange(row) {
      const option = this.countryOptions.find(item => item.key === row.countryKey)
      row.country = option ? option.country : ''
      row.countryEnglish = option ? option.countryEnglish : ''
    },
    removeTaxRate(index) {
      this.taxRateRows.splice(index, 1)
    },
    saveTaxRates() {
      const selectedKeys = this.taxRateRows.filter(item => item.countryKey).map(item => item.countryKey)
      if (new Set(selectedKeys).size !== selectedKeys.length) {
        this.$modal.msgWarning('同一个国家/地区请不要重复添加')
        return
      }
      const rows = this.taxRateRows
        .filter(item => item.countryKey)
        .map(item => ({
          country: (item.country || '').trim(),
          countryEnglish: (item.countryEnglish || '').trim(),
          taxRate: Number(item.taxPercent || 0) / 100
        }))
      if (!rows.length) {
        this.$modal.msgWarning('请先选择国家/地区')
        return
      }
      if (rows.some(item => !item.country)) {
        this.$modal.msgWarning('请选择区域化定价模板中的国家/地区')
        return
      }
      this.savingTaxRates = true
      saveRegionalPriceTaxRates(rows).then(res => {
        this.taxRateRows = this.normalizeTaxRateRows(res.rows || [])
        this.taxRateDialogVisible = false
        this.$modal.msgSuccess('默认税率已保存')
      }).finally(() => {
        this.savingTaxRates = false
      })
    },
    handleRegionChange(file, fileList) {
      this.regionFile = file.raw
      this.regionFiles = fileList.slice(-1)
    },
    handleFreightChange(file, fileList) {
      this.freightFile = file.raw
      this.freightFiles = fileList.slice(-1)
    },
    handleRegionRemove() {
      this.regionFile = null
      this.regionFiles = []
    },
    handleFreightRemove() {
      this.freightFile = null
      this.freightFiles = []
    },
    handleUploadTemplates() {
      if (!this.regionFile && !this.freightFile) {
        this.$modal.msgWarning('请选择要更新的模板')
        return
      }
      const data = new FormData()
      if (this.regionFile) {
        data.append('regionFile', this.regionFile)
      }
      if (this.freightFile) {
        data.append('freightFile', this.freightFile)
      }
      this.uploading = true
      uploadRegionalPriceTemplates(data).then(res => {
        this.templateStatus = {
          regionTemplate: res.regionTemplate || { exists: false },
          freightTemplate: res.freightTemplate || { exists: false }
        }
        this.regionFile = null
        this.freightFile = null
        this.regionFiles = []
        this.freightFiles = []
        this.$modal.msgSuccess('默认模板已更新')
      }).finally(() => {
        this.uploading = false
      })
    },
    handleCalculate() {
      if (this.form.commissionRate + this.form.profitRate >= 100) {
        this.$modal.msgWarning('佣金率% + 利润率% 必须小于 100')
        return
      }

      this.loading = true
      const payload = {
        ...this.form,
        commissionRate: this.form.commissionRate / 100,
        profitRate: this.form.profitRate / 100
      }
      calculateRegionalPrice(payload).then(res => {
        this.result = {
          rows: res.rows || [],
          warnings: res.warnings || 0,
          sheetName: res.sheetName || ''
        }
        this.copyText = res.copyText || ''
        if (res.params) {
          this.form.weightKg = res.params.weightKg
          this.form.commissionRate = this.toPercent(res.params.commissionRate)
          this.form.costRmb = res.params.costRmb
          this.form.exchangeRate = res.params.exchangeRate
          this.form.discount = res.params.discount
          this.form.profitRate = this.toPercent(res.params.profitRate)
        }
      }).finally(() => {
        this.loading = false
      })
    },
    toPercent(value) {
      return Math.round((Number(value || 0) * 100) * 100) / 100
    },
    copyResult() {
      const text = this.copyText
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => this.$modal.msgSuccess('已复制'))
        return
      }
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      this.$modal.msgSuccess('已复制')
    }
  }
}
</script>

<style scoped>
.regional-price-page {
  min-width: 920px;
}

.price-form {
  max-width: 1760px;
}

.price-form ::v-deep .el-form-item {
  margin-bottom: 18px;
}

.price-form ::v-deep .el-form-item__label {
  padding-right: 12px;
  font-weight: 600;
  color: #4b5563;
}

.cargo-type-group {
  display: flex;
  width: 100%;
}

.cargo-type-group ::v-deep .el-radio-button {
  flex: 1;
}

.cargo-type-group ::v-deep .el-radio-button__inner {
  width: 100%;
}

.upload-row {
  margin-top: 12px;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
}

.tax-rate-actions {
  margin-top: 12px;
}

.danger-link {
  color: #f56c6c;
}

.template-toggle {
  margin-top: 4px;
}

.result-alert {
  margin-bottom: 12px;
}

.copy-box {
  margin-bottom: 12px;
}

.template-alert {
  margin-top: 4px;
}

::v-deep .el-upload,
::v-deep .el-upload-dragger {
  width: 100%;
}
</style>
