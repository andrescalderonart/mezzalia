/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      // 2026-11-27, rediseño "startup": neutros limpios para que manden las
      // fotos de comida y el color propio de cada restaurante (--color-acento).
      // Se conservan los nombres papel/carbon para no tocar cada clase: papel =
      // superficies claras, carbon = tinta. Sin crema ni terracota por defecto.
      colors: {
        papel: {
          50: '#ffffff',
          100: '#ffffff',
          200: '#f4f4f2',
          300: '#e7e7e4',
        },
        carbon: {
          950: '#18181b',
          900: '#27272a',
          800: '#3f3f46',
          700: '#52525b',
          600: '#71717a',
        },
        sin: {
          DEFAULT: '#dc2626',
          suave: '#fef2f2',
        },
      },
      fontFamily: {
        display: ['Geist', 'system-ui', 'sans-serif'],
        sans: ['Geist', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        flotante: '0 10px 30px -10px rgba(24,24,27,0.35), 0 2px 6px rgba(24,24,27,0.08)',
        carta: '0 1px 2px rgba(13,12,11,0.06), 0 8px 20px -10px rgba(13,12,11,0.18)',
      },
      keyframes: {
        fadeInUp: {
          '0%': { opacity: 0, transform: 'translateY(8px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        // Vista de ingredientes: "esto no se puede quitar".
        sacudir: {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%, 60%': { transform: 'translateX(-7px)' },
          '40%, 80%': { transform: 'translateX(7px)' },
        },
      },
      animation: {
        fadeInUp: 'fadeInUp 0.28s cubic-bezier(.2,.8,.2,1) both',
        sacudir: 'sacudir 0.4s ease-in-out',
      },
    },
  },
  plugins: [],
}
