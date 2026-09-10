import Button from './Button'

export default function StatusMessage({ type = 'empty', message, onRetry }) {
  return <div className={`status-message status-message--${type}`} role={type === 'error' ? 'alert' : 'status'}><p>{message || (type === 'loading' ? 'Cargando...' : 'No hay datos disponibles.')}</p>{type === 'error' && onRetry && <Button variant="secondary" onClick={onRetry}>Reintentar</Button>}</div>
}
