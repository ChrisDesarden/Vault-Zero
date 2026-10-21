package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun buildMaterialIcon(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        pathFillType = PathFillType.NonZero,
        name = name,
        fill = SolidColor(Color.Black),
        fillAlpha = 1.0f,
        stroke = null,
        strokeAlpha = 1.0f,
        strokeLineWidth = 1.0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Bevel,
        strokeLineMiter = 1.0f
    ).build()
}

private var _autoAwesome: ImageVector? = null
val Icons.Filled.AutoAwesome: ImageVector
    get() = _autoAwesome ?: buildMaterialIcon(
        "Filled.AutoAwesome",
        "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
    ).also { _autoAwesome = it }

private var _badge: ImageVector? = null
val Icons.Filled.Badge: ImageVector
    get() = _badge ?: buildMaterialIcon(
        "Filled.Badge",
        "M20,7h-5V4c0,-1.1 -0.9,-2 -2,-2h-2C9.9,2 9,2.9 9,4v3H4C2.9,7 2,7.9 2,9v11c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V9C22,7.9 21.1,7 20,7zM9,12c0.83,0 1.5,0.67 1.5,1.5S9.83,15 9,15s-1.5,-0.67 -1.5,-1.5S8.17,12 9,12zM12,18H6v-0.75c0,-1 2,-1.5 3,-1.5s3,0.5 3,1.5V18zM13,9h-2V4h2V9zM18,16.5h-4V15h4V16.5zM18,13.5h-4V12h4V13.5z"
    ).also { _badge = it }

private var _code: ImageVector? = null
val Icons.Filled.Code: ImageVector
    get() = _code ?: buildMaterialIcon(
        "Filled.Code",
        "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z"
    ).also { _code = it }

private var _contentCopy: ImageVector? = null
val Icons.Filled.ContentCopy: ImageVector
    get() = _contentCopy ?: buildMaterialIcon(
        "Filled.ContentCopy",
        "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"
    ).also { _contentCopy = it }

private var _creditCard: ImageVector? = null
val Icons.Filled.CreditCard: ImageVector
    get() = _creditCard ?: buildMaterialIcon(
        "Filled.CreditCard",
        "M20,4H4C2.89,4 2.01,4.89 2.01,6L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V6C22,4.89 21.11,4 20,4zM20,18H4v-6h16V18zM20,8H4V6h16V8z"
    ).also { _creditCard = it }

private var _currencyBitcoin: ImageVector? = null
val Icons.Filled.CurrencyBitcoin: ImageVector
    get() = _currencyBitcoin ?: buildMaterialIcon(
        "Filled.CurrencyBitcoin",
        "M17.06,11.57C17.65,10.88 18,9.98 18,9c0,-1.86 -1.27,-3.43 -3,-3.87V3h-2v2h-2V3H9v2H6v2h2v10H6v2h3v2h2v-2h2v2h2v-2c2.21,0 4,-1.79 4,-4C19,13.55 18.22,12.27 17.06,11.57zM10,7h4c1.1,0 2,0.9 2,2s-0.9,2 -2,2h-4V7zM15,17h-5v-4h5c1.1,0 2,0.9 2,2S16.1,17 15,17z"
    ).also { _currencyBitcoin = it }

private var _deleteForever: ImageVector? = null
val Icons.Filled.DeleteForever: ImageVector
    get() = _deleteForever ?: buildMaterialIcon(
        "Filled.DeleteForever",
        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM8.46,11.88l1.41,-1.41L12,12.59l2.12,-2.12 1.41,1.41L13.41,14l2.12,2.12 -1.41,1.41L12,15.41l-2.12,2.12 -1.41,-1.41L10.59,14L8.46,11.88zM15.5,4l-1,-1h-5l-1,1H5v2h14V4H15.5z"
    ).also { _deleteForever = it }

private var _description: ImageVector? = null
val Icons.Filled.Description: ImageVector
    get() = _description ?: buildMaterialIcon(
        "Filled.Description",
        "M14,2H6C4.9,2 4.01,2.9 4.01,4L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8L14,2zM16,18H8v-2h8V18zM16,14H8v-2h8V14zM13,9V3.5L18.5,9H13z"
    ).also { _description = it }

private var _download: ImageVector? = null
val Icons.Filled.Download: ImageVector
    get() = _download ?: buildMaterialIcon(
        "Filled.Download",
        "M5,20h14v-2H5V20zM19,9h-4V3H9v6H5l7,7L19,9z"
    ).also { _download = it }

private var _error: ImageVector? = null
val Icons.Filled.Error: ImageVector
    get() = _error ?: buildMaterialIcon(
        "Filled.Error",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2V17zM13,13h-2V7h2V13z"
    ).also { _error = it }

private var _fingerprint: ImageVector? = null
val Icons.Filled.Fingerprint: ImageVector
    get() = _fingerprint ?: buildMaterialIcon(
        "Filled.Fingerprint",
        "M17.81,4.47c-0.08,0 -0.16,-0.02 -0.23,-0.06C15.66,3.42 14,3 12.01,3c-1.98,0 -3.86,0.47 -5.57,1.41 -0.24,0.13 -0.54,0.04 -0.68,-0.2 -0.13,-0.24 -0.04,-0.55 0.2,-0.68C7.82,2.52 9.86,2 12.01,2c2.13,0 3.99,0.47 6.03,1.52 0.25,0.13 0.34,0.43 0.21,0.67 -0.09,0.18 -0.26,0.28 -0.44,0.28zM3.5,9.72c-0.1,0 -0.2,-0.03 -0.29,-0.09 -0.23,-0.16 -0.28,-0.47 -0.12,-0.7 0.99,-1.4 2.25,-2.5 3.75,-3.27C9.98,4.04 14,4.03 17.15,5.65c1.5,0.77 2.76,1.86 3.75,3.25 0.16,0.22 0.11,0.54 -0.12,0.7 -0.23,0.16 -0.54,0.11 -0.7,-0.12 -0.89,-1.25 -2.02,-2.23 -3.39,-2.94 -2.87,-1.47 -6.54,-1.47 -9.4,0.01 -1.36,0.7 -2.5,1.7 -3.38,2.96 -0.1,0.14 -0.26,0.21 -0.41,0.21zM9.75,21.79c-0.13,0 -0.26,-0.05 -0.35,-0.15 -0.87,-0.87 -1.34,-1.43 -2.01,-2.64 -0.69,-1.23 -1.05,-2.73 -1.05,-4.34 0,-2.97 2.54,-5.39 5.66,-5.39s5.66,2.42 5.66,5.39c0,0.28 -0.22,0.5 -0.5,0.5s-0.5,-0.22 -0.5,-0.5c0,-2.42 -2.09,-4.39 -4.66,-4.39 -2.57,0 -4.66,1.97 -4.66,4.39 0,1.44 0.32,2.77 0.93,3.85 0.64,1.15 1.08,1.64 1.85,2.42 0.19,0.2 0.19,0.51 0,0.71 -0.1,0.1 -0.23,0.15 -0.37,0.15zM16.92,19.94c-1.19,0 -2.24,-0.3 -3.1,-0.89 -1.49,-1.01 -2.38,-2.65 -2.38,-4.39 0,-0.28 0.22,-0.5 0.5,-0.5s0.5,0.22 0.5,0.5c0,1.41 0.72,2.74 1.94,3.56 0.71,0.48 1.54,0.71 2.54,0.71 0.24,0 0.64,-0.03 1.04,-0.1 0.27,-0.05 0.53,0.13 0.58,0.41 0.05,0.27 -0.13,0.53 -0.41,0.58 -0.57,0.11 -1.07,0.12 -1.21,0.12zM14.91,22c-0.04,0 -0.09,-0.01 -0.13,-0.02 -1.59,-0.44 -2.63,-1.03 -3.72,-2.1 -1.4,-1.39 -2.17,-3.24 -2.17,-5.22 0,-1.62 1.38,-2.94 3.08,-2.94 1.7,0 3.08,1.32 3.08,2.94 0,1.07 0.93,1.94 2.08,1.94s2.08,-0.87 2.08,-1.94c0,-3.77 -3.25,-6.83 -7.25,-6.83 -2.84,0 -5.44,1.58 -6.61,4.03 -0.39,0.81 -0.59,1.76 -0.59,2.8 0,0.78 0.07,2.01 0.67,3.61 0.1,0.26 -0.03,0.55 -0.29,0.64 -0.26,0.1 -0.55,-0.04 -0.64,-0.29 -0.49,-1.31 -0.73,-2.61 -0.73,-3.96 0,-1.2 0.23,-2.29 0.68,-3.24 1.33,-2.79 4.28,-4.6 7.51,-4.6 4.55,0 8.25,3.51 8.25,7.83 0,1.62 -1.38,2.94 -3.08,2.94s-3.08,-1.32 -3.08,-2.94c0,-1.07 -0.93,-1.94 -2.08,-1.94s-2.08,0.87 -2.08,1.94c0,1.71 0.66,3.31 1.87,4.51 0.95,0.94 1.88,1.46 3.27,1.85 0.27,0.07 0.42,0.35 0.35,0.61 -0.05,0.23 -0.26,0.38 -0.48,0.38z"
    ).also { _fingerprint = it }

private var _key: ImageVector? = null
val Icons.Filled.Key: ImageVector
    get() = _key ?: buildMaterialIcon(
        "Filled.Key",
        "M21,10h-8.35C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H13l2,2 2,-2 2,2 4,-4.04L21,10zM7,15c-1.65,0 -3,-1.35 -3,-3s1.35,-3 3,-3 3,1.35 3,3 -1.35,3 -3,3z"
    ).also { _key = it }

private var _lockOpen: ImageVector? = null
val Icons.Filled.LockOpen: ImageVector
    get() = _lockOpen ?: buildMaterialIcon(
        "Filled.LockOpen",
        "M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12V20z"
    ).also { _lockOpen = it }

private var _save: ImageVector? = null
val Icons.Filled.Save: ImageVector
    get() = _save ?: buildMaterialIcon(
        "Filled.Save",
        "M17,3H5C3.89,3 3,3.9 3,5v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7L17,3zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10V9z"
    ).also { _save = it }

private var _shield: ImageVector? = null
val Icons.Filled.Shield: ImageVector
    get() = _shield ?: buildMaterialIcon(
        "Filled.Shield",
        "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5L12,1z"
    ).also { _shield = it }

private var _timer: ImageVector? = null
val Icons.Filled.Timer: ImageVector
    get() = _timer ?: buildMaterialIcon(
        "Filled.Timer",
        "M15,1H9v2h6V1zM11,14h2V8h-2V14zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9C21,10.88 20.26,8.93 19.03,7.39zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"
    ).also { _timer = it }

private var _upload: ImageVector? = null
val Icons.Filled.Upload: ImageVector
    get() = _upload ?: buildMaterialIcon(
        "Filled.Upload",
        "M5,20h14v-2H5V20zM5,10h4v6h6v-6h4l-7,-7L5,10z"
    ).also { _upload = it }

private var _visibility: ImageVector? = null
val Icons.Filled.Visibility: ImageVector
    get() = _visibility ?: buildMaterialIcon(
        "Filled.Visibility",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
    ).also { _visibility = it }

private var _visibilityOff: ImageVector? = null
val Icons.Filled.VisibilityOff: ImageVector
    get() = _visibilityOff ?: buildMaterialIcon(
        "Filled.VisibilityOff",
        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z"
    ).also { _visibilityOff = it }

private var _vpnKey: ImageVector? = null
val Icons.Filled.VpnKey: ImageVector
    get() = _vpnKey ?: buildMaterialIcon(
        "Filled.VpnKey",
        "M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"
    ).also { _vpnKey = it }
