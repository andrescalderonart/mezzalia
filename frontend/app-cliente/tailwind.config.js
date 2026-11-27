/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Mismos valores de marca que frontend/app-admin/tailwind.config.js
        // (logo terracota/dorado sobre negro) -- pero invertidos de rol: acá
        // "papel" (claro, cálido) es el fondo, no "carbon". Una carta digital
        // se lee con luz de restaurante variable (ventana, sol de terraza,
        // luz de vela) y se decide qué pedir mirando fotos de comida; un
        // tema oscuro tipo panel-de-control no es lo más apetecible ni lo
        // más legible ahí, a diferencia del panel de administración interno.
        papel: {
          50: '#fffdf9',
          100: '#faf6ef',
          200: '#f0e6d2',
          300: '#e4d4b5',
        },
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
        sans: ['Sora', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
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
        fadeInUp: 'fadeInUp 0.35s ease-out both',
        sacudir: 'sacudir 0.4s ease-in-out',
      },
    },
  },
  plugins: [],
}
