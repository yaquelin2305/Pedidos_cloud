import { useCallback, useEffect, useRef, useState } from 'react'

export default function useCollection(list) {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const sequence = useRef(0)
  const active = useRef(false)
  const busy = useRef(false)

  const reload = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setError('')
    try {
      const result = await list()
      if (active.current && request === sequence.current) setData(result)
    } catch (failure) {
      if (active.current && request === sequence.current) setError(failure.message)
    } finally {
      if (active.current && request === sequence.current) setLoading(false)
    }
  }, [list])

  useEffect(() => {
    active.current = true
    reload()
    return () => { active.current = false; sequence.current++ }
  }, [reload])

  async function mutate(operation) {
    if (busy.current) throw new Error('Hay una operación en curso.')
    busy.current = true
    setPending(true)
    try {
      const result = await operation()
      if (active.current) await reload()
      return result
    } finally {
      busy.current = false
      if (active.current) setPending(false)
    }
  }

  return { data, loading, error, pending, reload, mutate }
}
