import useCollection from '../../../shared/utils/useCollection'
import { ordersService } from '../services/ordersService'

export default function useOrderProducts() {
  const { data, loading, error, reload } = useCollection(ordersService.listProducts)
  return { products: data, loading, error, reload }
}
