/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Paleta derivada del logo de Mezzalia (circulo terracota/dorado
        // sobre negro). "carbon" = fondo casi negro, nunca gris puro frio.
        carbon: {
          950: '#0d0c0b',
          900: '#141311',
          800: '#1d1b18',
          700: '#2a2723',
          600: '#3d3933',
        },
        crema: '#f3ecdf',
        terracota: {
          DEFAULT: '#c1502e',
          light: '#d97a52',
          dark: '#8f3a20',
        },
        dorado: {
          DEFAULT: '#d4a24c',
          light: '#e6c17a',
        },
      },
      fontFamily: {
        display: ['"Playfair Display"', 'serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(212,162,76,0.25), 0 8px 24px -8px rgba(193,80,46,0.35)',
      },
      keyframes: {
        fadeInUp: {
          '0%': { opacity: 0, transform: 'translateY(8px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: 1 },
          '50%': { opacity: 0.55 },
        },
        shake: {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%, 60%': { transform: 'translateX(-6px)' },
          '40%, 80%': { transform: 'translateX(6px)' },
        },
      },
      animation: {
        fadeInUp: 'fadeInUp 0.35s ease-out both',
        pulseSoft: 'pulseSoft 1.4s ease-in-out infinite',
        shake: 'shake 0.4s ease-in-out',
      },
    },
  },
  plugins: [],
}
