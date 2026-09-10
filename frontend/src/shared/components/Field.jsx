export default function Field({ id, label, error, children }) {
  return <div className="field"><label htmlFor={id}>{label}</label>{children}{error && <p id={`${id}-error`} className="field-error" role="alert">{error}</p>}</div>
}
