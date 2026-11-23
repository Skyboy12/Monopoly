import logoImage from '../../assets/logo.svg'

export default function Logo({ small = false }) {
  return (
    <img
      className={`brand-logo ${small ? 'brand-logo-small' : ''}`}
      src={logoImage}
      alt="MONOPOLY PARTY - Tabletop Edition"
    />
  )
}
