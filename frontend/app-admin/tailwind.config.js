/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      // 2026-11-27, rediseño "startup": neutros oscuros (zinc) y un solo
      // acento naranja derivado de la marca. Se conservan los nombres
      // carbon/crema/dorado/terracota para no tocar cada clase del panel.
      colors: {
        carbon: {
          950: '#09090b',
          900: '#111113',
          800: '#18181b',
          700: '#27272a',
          600: '#3f3f46',
        },
        crema: '#fafafa',
        terracota: {
          DEFAULT: '#f97316',
          light: '#fb923c',
          dark: '#c2410c',
        },
        dorado: {
          DEFAULT: '#fb923c',
          light: '#fdba74',
        },
        sin: {
          DEFAULT: '#f87171',
          fondo: 'rgba(248,113,113,0.12)',
        },
      },
      fontFamily: {
        display: ['Geist', 'system-ui', 'sans-serif'],
        sans: ['Geist', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        glow: '0 1px 2px rgba(0,0,0,0.4)',
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
