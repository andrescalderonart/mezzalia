# Guía de fotos para la vista de ingredientes

Cómo producir con IA (Google Flow) las fotos que usa la vista "plato desarmado" de
la carta. Los prompts van en inglés porque Flow los interpreta mejor.

## Reglas generales

- **Una imagen por ingrediente.** Si sale algo extra, repetir agregando
  `only one ingredient, nothing else`.
- **Fondo blanco puro, sin sombra, sin plato.** El panel le quita el fondo al subirla.
  Para ingredientes blancos (arroz, queso, crema, mayonesa) usar
  `pure light gray (#E5E5E5) seamless background`.
- **Usar la foto real del plato como referencia** en Flow siempre que exista, para
  que los ingredientes se parezcan a los del restaurante. Sin foto, borrar la primera
  frase del prompt ("Using the reference…").
- **Nombrar los archivos** `plato-numero-ingrediente.png` (ej. `perro-3-queso.png`),
  en el orden en que van.

## Las 5 plantillas

### T1 · Imagen maestra desarmada (solo platos apilados)

```text
Using the reference photo, deconstruct this exact dish into floating horizontal layers, perfectly aligned vertically and evenly spaced, side view with the camera slightly above (10 degrees). Keep each ingredient looking exactly like in the reference. Order from top to bottom: [CAPAS]. Each layer clearly separated with empty space between them, nothing touching. Pure white seamless background, soft even studio lighting, no shadows, no plate, no hands, no text. Ultra sharp, high detail, appetizing, vertical 9:16.
```

### T2 · Una capa sola (con la imagen maestra como referencia)

```text
Using the reference image, isolate ONLY the [INGREDIENTE] layer. Keep exactly the same angle, lighting, size, proportions and appearance as in the reference. Remove every other ingredient. Center it horizontally, occupying about 70% of the image width. Pure white seamless background, no shadow, no plate, no hands, no text. Horizontal 16:9, ultra sharp, high detail food photography.
```

### T3 · Un componente de plato esparcido (bandejas, picadas, sopas, acompañamientos)

```text
Using the reference photo of this dish, show ONLY the [INGREDIENTE] exactly as it appears in the dish (same cut, color, cooking and portion size), on its own. Viewed from above at a 45 degree angle, centered, occupying 70% of the frame. Pure white seamless background, soft even studio lighting, no shadow, no plate, no bowl, no hands, no text. Square 1:1, ultra sharp, high detail, appetizing.
```

### T4 · Salsas y toppings líquidos

```text
Professional food photography of a small neat dollop of [SALSA], glossy and thick, on its own. Viewed from above at a 45 degree angle, centered, occupying 50% of the frame. Pure white seamless background, soft even studio lighting, no shadow, no bowl, no spoon, no hands, no text. Square 1:1, ultra sharp, high detail.
```

Para platos **apilados** (perros, patacones), la salsa como capa:
`a zigzag drizzle of [SALSA], as a flat horizontal layer` con T2.

### T5 · Foto del plato completo (la que va arriba en la vista esparcida y en la carta)

```text
Professional food photography of [PLATO], Colombian style, beautifully served on a simple plate, viewed from above at a 45 degree angle, natural soft daylight, shallow depth of field, warm and appetizing, clean wooden table, no hands, no text. Horizontal 4:3, ultra sharp, high detail.
```

---

## Comidas rápidas

### Perro caliente colombiano · APILADO
T1 con `[CAPAS]` = `crushed potato chips, pink sauce drizzle, pineapple sauce drizzle, melted grated cheese, chopped white onion, grilled sausage, hot dog bun`.
Capas con T2:

| # | Capa | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|---|
| 1 | Ripio de papa | `crushed potato chips topping` | Sí |
| 2 | Salsa rosada | `zigzag drizzle of pink sauce, as a flat horizontal layer` | Sí |
| 3 | Salsa de piña | `zigzag drizzle of pineapple sauce, as a flat horizontal layer` | Sí |
| 4 | Queso | `melted grated mozzarella cheese layer` | Sí |
| 5 | Cebolla | `finely chopped white onion layer` | Sí |
| 6 | Salchicha | `grilled hot dog sausage` | No (incluido) |
| 7 | Pan | `soft hot dog bun, opened` | No (incluido) |

### Salchipapa · ESPARCIDO
Foto del plato con T5 (`a Colombian salchipapa: french fries with sliced sausage and sauces`). Componentes con T3/T4:

| Componente | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|
| Papas a la francesa | `french fries` | No (incluido) |
| Salchicha | `sliced fried sausage rounds` | No (incluido) |
| Queso rallado | `grated white cheese` (fondo gris) | Sí |
| Salsa rosada | T4: `pink sauce` | Sí |
| Salsa tártara | T4: `tartar sauce` (fondo gris) | Sí |
| Salsa de piña | T4: `pineapple sauce` | Sí |
| Huevos de codorniz | `three peeled boiled quail eggs` | Sí |
| Ripio de papa | `crushed potato chips` | Sí |

### Patacón con todo · APILADO
T1 con `[CAPAS]` = `pink sauce drizzle, melted gratinated cheese, shredded chicken, shredded beef, hogao tomato and onion sauce, large flat fried green plantain patacón`.

| # | Capa | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|---|
| 1 | Salsa rosada | `zigzag drizzle of pink sauce, as a flat horizontal layer` | Sí |
| 2 | Queso gratinado | `melted gratinated cheese layer` | Sí |
| 3 | Pollo desmechado | `shredded chicken layer` | Sí |
| 4 | Carne desmechada | `shredded beef layer` | Sí |
| 5 | Hogao | `hogao, Colombian tomato and onion sauce, as a flat layer` | Sí |
| 6 | Patacón | `large flat fried green plantain patacón` | No (incluido) |

### Arepa rellena de carne desmechada · APILADO
T1 con `[CAPAS]` = `top half of a grilled corn arepa, melted white cheese, shredded beef, hogao sauce, sliced avocado, bottom half of the arepa`.

| # | Capa | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|---|
| 1 | Arepa (tapa) | `top half of a grilled white corn arepa` | No (incluido) |
| 2 | Queso | `melted white cheese slice` (fondo gris) | Sí |
| 3 | Carne desmechada | `shredded beef layer` | No (incluido) |
| 4 | Hogao | `hogao tomato and onion sauce layer` | Sí |
| 5 | Aguacate | `avocado slices, side by side` | Sí |
| 6 | Arepa (base) | `bottom half of a grilled white corn arepa` | No (incluido) |

### Sándwich club · APILADO
T1 con `[CAPAS]` = `toasted bread slice, lettuce, tomato slices, crispy bacon, grilled chicken breast slices, ham, cheese slice, toasted bread slice`.

| # | Capa | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|---|
| 1 | Pan de arriba | `toasted white bread slice` | No (incluido) |
| 2 | Lechuga | `crisp green lettuce leaves, flat layer` | Sí |
| 3 | Tomate | `two tomato slices` | Sí |
| 4 | Tocineta | `crispy bacon strips` | Sí |
| 5 | Pollo | `grilled chicken breast slices` | No (incluido) |
| 6 | Jamón | `folded ham slices` | Sí |
| 7 | Queso | `cheddar cheese slice` | Sí |
| 8 | Pan de abajo | `toasted white bread slice` | No (incluido) |

### Picada / fritanga · ESPARCIDO
Foto del plato con T5 (`a Colombian picada platter with fried meats, potatoes and plantains`). Todo con T3, todo se puede quitar salvo lo que el restaurante decida:
`grilled chorizo slices`, `morcilla blood sausage slices`, `crispy pork belly chicharrón`, `small fried yellow creole potatoes (papa criolla)`, `small grilled corn arepas`, `fried green plantain patacones`, `fried ripe plantain (maduro)`, `lime wedges`, `small bowl-free portion of Colombian ají sauce`.

### Mazorcada · ESPARCIDO
Foto con T5 (`a Colombian mazorcada: corn kernels topped with meats, melted cheese, potato chips and sauces`).

| Componente | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|
| Maíz tierno | `sweet corn kernels` | No (incluido) |
| Pollo desmechado | `shredded chicken` | Sí |
| Carne | `diced grilled beef` | Sí |
| Tocineta | `crispy bacon pieces` | Sí |
| Queso gratinado | `melted gratinated cheese` | Sí |
| Ripio de papa | `crushed potato chips` | Sí |
| Salsas | T4: `pink sauce`, `pineapple sauce` | Sí |

### Alitas · ESPARCIDO
Foto con T5. `chicken wings glazed in barbecue sauce` (incluido); quitables: T4 `ranch dressing`, `celery sticks`, `carrot sticks`.

### Empanadas · ESPARCIDO (casi informativo)
Foto con T5. `golden fried Colombian corn empanadas` (incluido); quitables: T4 `Colombian ají sauce`, `lime wedges`.

---

## Platos típicos

### Bandeja paisa · ESPARCIDO
Foto con T5 (`a traditional Colombian bandeja paisa`). Componentes con T3:

| Componente | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|
| Arroz | `white rice portion` (fondo gris) | No (incluido) |
| Frijoles | `red beans (frijoles) in their thick sauce` | Sí |
| Carne molida | `seasoned ground beef` | Sí |
| Chicharrón | `crispy pork belly chicharrón` | Sí |
| Chorizo | `grilled chorizo` | Sí |
| Huevo frito | `fried egg with runny yolk` | Sí |
| Plátano maduro | `fried ripe plantain slices (maduro)` | Sí |
| Aguacate | `sliced avocado` | Sí |
| Arepa | `small white corn arepa` | Sí |

### Ajiaco santafereño · ESPARCIDO
Foto con T5 (`a bowl of Colombian ajiaco santafereño soup`). Para lo cocinado adentro usar T3 cambiando `exactly as it appears in the dish` por `exactly as it looks cooked inside the soup, slightly glossy from the broth`.

| Componente | `[INGREDIENTE]` | ¿Se puede quitar? |
|---|---|---|
| Pollo desmechado | `shredded chicken` | No (incluido) |
| Papa criolla | `small yellow creole potatoes` | No (incluido) |
| Papa sabanera y pastusa | `chunks of white and red potatoes` | No (incluido) |
| Mazorca | `corn on the cob slice` | No (incluido) |
| Guascas | `dried guascas herb leaves` | No (incluido) |
| Crema de leche | T4: `heavy cream` (fondo gris) | Sí |
| Alcaparras | `capers` | Sí |
| Aguacate | `sliced avocado` | Sí |
| Arroz | `white rice portion` (fondo gris) | Sí |

### Sancocho · ESPARCIDO
Foto con T5 (`a bowl of Colombian sancocho soup`). Cocinado adentro con la variante de sopa:
`beef rib pieces` (incluido), `small yellow creole potatoes` (incluido), `yuca chunks` (incluido), `corn on the cob slices` (incluido), `green plantain pieces` (incluido), `fresh chopped cilantro` (sí).
Acompañamientos con T3: `white rice portion` (sí), `sliced avocado` (sí), `lime wedges` (sí).

### Mondongo · ESPARCIDO
Foto con T5 (`a bowl of Colombian mondongo soup`). Incluidos (variante de sopa): `tripe pieces`, `potato chunks`, `yuca chunks`, `carrot slices`, `chorizo slices`. Acompañamientos (sí): `white rice portion`, `sliced avocado`, `banana`, T4 `Colombian ají sauce`.

### Mojarra frita · ESPARCIDO
Foto con T5 (`a whole fried mojarra fish with patacones and coconut rice`).
`whole crispy fried mojarra fish` (incluido); quitables: `fried green plantain patacones`, `coconut rice portion`, `fresh tomato and onion salad`, `lime wedges`.

### Lechona · ESPARCIDO (casi informativo)
Foto con T5 (`a serving of Colombian lechona tolimense`).
Incluidos: `crispy pork skin piece`, `seasoned pork meat stuffed with yellow peas and rice`. Quitables: `small white corn arepa`, `insulso (Colombian sweet corn dessert) small portion`.

### Tamal tolimense · ESPARCIDO (informativo)
Foto con T5 (`an opened Colombian tamal tolimense on its banana leaf`). Todo incluido:
`corn masa`, `pork rib piece`, `chicken piece`, `boiled egg half`, `carrot slices`, `yellow peas`. Acompañamientos (sí): `small white corn arepa`, `cup-free portion of hot chocolate` (o quitarlo si no aplica).

---

## Bebidas

### Michelada · ESPARCIDO
Foto con T5 (`a Colombian michelada beer cocktail in a salt-rimmed glass with lime`).
`cold lager beer in a glass` (incluido); quitables con T3/T4: `coarse salt crystals`, `lime wedges`, `chili-lime powder (tajín)`, `a small splash of worcestershire sauce`, `ice cubes`.
