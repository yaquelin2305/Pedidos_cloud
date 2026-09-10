import { useCallback, useEffect, useRef, useState } from 'react'
import { ordersService } from '../services/ordersService'

export default function useOrderDetail(id, revision) {
  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const sequence = useRef(0)
  const reload = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setError('')
    try {
      const result = await ordersService.getById(id)
      if (request === sequence.current) setOrder(result)
    } catch (failure) {
      if (request === sequence.current) setError(failure.message)
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [id])
  useEffect(() => { reload(); return () => { sequence.current++ } }, [reload, revision])
  return { order, loading, error, reload }
}
