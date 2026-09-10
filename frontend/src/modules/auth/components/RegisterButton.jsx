import Button from '../../../shared/components/Button'

export default function RegisterButton({ onClick, disabled }) {
  return <Button variant="secondary" onClick={onClick} disabled={disabled}>Crear cuenta</Button>
}
