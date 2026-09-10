import useCollection from '../../../shared/utils/useCollection'
import { catalogService } from '../services/catalogService'

export default function useProducts() {
  const { data, mutate, ...state } = useCollection(catalogService.list)
  return {
    products: data, ...state,
    getById: catalogService.getById,
    create: (input) => mutate(() => catalogService.create(input)),
    update: (id, input) => mutate(() => catalogService.update(id, input)),
    adjustStock: (id, stock) => mutate(() => catalogService.adjustStock(id, stock)),
    remove: (id) => mutate(() => catalogService.remove(id)),
  }
}
