import useCollection from '../../../shared/utils/useCollection'
import { ordersService } from '../services/ordersService'

export default function useOrders() {
  const { data, mutate, ...state } = useCollection(ordersService.list)
  return {
    orders: data, ...state,
    getById: ordersService.getById,
    create: (input) => mutate(() => ordersService.create(input)),
    changeStatus: (id, status) => mutate(() => ordersService.changeStatus(id, status)),
  }
}
