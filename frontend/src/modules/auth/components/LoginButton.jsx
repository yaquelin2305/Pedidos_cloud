import Button from '../../../shared/components/Button'

export default function LoginButton({ onClick, disabled }) {
  return <Button onClick={onClick} disabled={disabled}>Iniciar sesión con Microsoft</Button>
}
