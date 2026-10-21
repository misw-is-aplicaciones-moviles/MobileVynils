package co.edu.uniandes.vinilos.core.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta del kit v2 de los mockups (Figma "Vynils V2 MockUps", frame "Kit v2 · Componentes").
// Los composables nunca usan estos valores directamente: leen MaterialTheme.colorScheme.

/** Carbón: cabeceras, texto principal, botones primarios, barra de éxito. */
internal val Charcoal = Color(0xFF232323)

/** Crema: fondo de la hoja de contenido, barra inferior, texto sobre carbón. */
internal val Cream = Color(0xFFF2EFE7)

/** Piedra: fondo del botón deshabilitado y de la opción elegida. */
internal val Stone = Color(0xFFD9D6CE)

/** Línea: separadores y bordes suaves. */
internal val Line = Color(0xFFD2CFC7)

/** Grafito: texto secundario (artista, metadatos). */
internal val Graphite = Color(0xFF66645E)

/** Ceniza: iconos y etiquetas inactivos, bordes de campos. */
internal val Ash = Color(0xFF8A8780)

/** Variantes para el tema oscuro, derivadas de la misma paleta. */
internal val CharcoalRaised = Color(0xFF2E2E2C)
internal val CharcoalHigh = Color(0xFF3A3936)
