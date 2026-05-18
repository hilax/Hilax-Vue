import request from '@/utils/request'

export function calculateRegionalPrice(data) {
  return request({
    url: '/system/regional-price/calculate',
    method: 'post',
    data
  })
}

export function getRegionalPriceTemplates() {
  return request({
    url: '/system/regional-price/templates',
    method: 'get'
  })
}

export function uploadRegionalPriceTemplates(data) {
  return request({
    url: '/system/regional-price/templates',
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    data
  })
}

export function getRegionalPriceTaxRates() {
  return request({
    url: '/system/regional-price/tax-rates',
    method: 'get'
  })
}

export function getRegionalPriceCountries() {
  return request({
    url: '/system/regional-price/countries',
    method: 'get'
  })
}

export function saveRegionalPriceTaxRates(data) {
  return request({
    url: '/system/regional-price/tax-rates',
    method: 'post',
    data
  })
}
