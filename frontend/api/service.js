import http from '../utils/request.js'

export function getServices() {
  return http.get('/services')
}

export function getServiceById(id) {
  return http.get(`/services/${id}`)
}

export default {
  getServices,
  getServiceById
}
