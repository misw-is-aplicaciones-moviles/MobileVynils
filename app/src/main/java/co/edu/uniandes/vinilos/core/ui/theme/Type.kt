package co.edu.uniandes.vinilos.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import co.edu.uniandes.vinilos.R

// Tipografías del kit v2: Outfit para títulos y cuerpo, Montserrat para etiquetas en mayúsculas.
// Fuentes empaquetadas en res/font (licencia OFL en licenses/), sin depender de Google Play Services.

internal val Outfit =
    FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_medium, FontWeight.Medium),
    )

internal val Montserrat =
    FontFamily(
        Font(R.font.montserrat_semibold, FontWeight.SemiBold),
        Font(R.font.montserrat_bold, FontWeight.Bold),
        Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
    )

// Tamaños tomados de los estilos de texto más usados en los mockups (390 dp de ancho).
internal val VinilosTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 34.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 19.sp,
                lineHeight = 24.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                letterSpacing = 1.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 19.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 19.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                letterSpacing = 1.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 1.sp,
            ),
    )
