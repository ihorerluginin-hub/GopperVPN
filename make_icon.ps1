# Генерация иконок «Гоппер VPN».
#   powershell -ExecutionPolicy Bypass -File .\make_icon.ps1
# Требуется Windows PowerShell 5.1 + System.Drawing. Больше ничего.
#
# Силуэт щита и стрелки не задаётся точками, а берётся теми же строками
# pathData, что лежат в res/drawable/ic_app_shield.xml и ic_app_arrow.xml.
# Так иконка в лаунчере и знак на кнопке подключения гарантированно
# совпадают: раньше здесь был свой многоугольник, и щит на иконке был
# шире и ниже, чем на кнопке.

param(
    [string]$ResDir = (Join-Path $PSScriptRoot 'app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Вьюпорт, в котором заданы оба пути. Доли от него — единственное, что
# отличает знак на иконке от знака на кнопке.
$vpW = 268.0
$vpH = 296.1
$vpCX = $vpW / 2
$vpCY = $vpH / 2

$shieldPath = 'M0,30 Q0,0 30,0 L238,0 Q268,0 268,30 L268,120.6 Q268,142.6 261.4,163.6 L257.3,176.7 Q251.9,193.9 240.6,207.9 L229.8,221.3 Q219.8,233.8 206.1,242 L188.9,252.3 Q176.9,259.5 166.2,268.6 L134,296.1 L101.8,268.6 Q91.1,259.5 79.1,252.3 L61.9,242 Q48.2,233.8 38.2,221.3 L27.4,207.9 Q16.1,193.9 10.7,176.7 L6.6,163.6 Q0,142.6 0,120.6 Z'
$arrowPath = 'M134,51.3 L184.8,119.7 L156.7,119.7 L156.7,193.9 L111.3,193.9 L111.3,119.7 L83.2,119.7 Z'

# Фирменная зелёная палитра, те же значения что в res/values/colors.xml.
$clrBgDark    = [System.Drawing.Color]::FromArgb(255, 10, 112, 64)    # gopper_600
$clrBgLight   = [System.Drawing.Color]::FromArgb(255, 53, 185, 123)   # gopper_400
$clrShield    = [System.Drawing.Color]::White
$clrArrow     = [System.Drawing.Color]::FromArgb(255, 11, 138, 75)    # gopper_500
$clrShadow    = [System.Drawing.Color]::FromArgb(55, 0, 0, 0)

# Высота знака от стороны иконки.
#
# Считано под adaptive icon: холст 108dp, маска показывает центральные
# 72dp. При 0,66 высота знака была 71,3dp — он упирался в край маски, и
# скруглённые верхние углы щита срезало. Видно это и на TECNO, и на
# Redmi: белого щита почти на всю иконку, зелёного фона почти не
# остаётся.
#
# Теперь 0,55 — знак 59,4dp при ширине 53,8dp. От края маски сверху и
# снизу остаётся по 6,3dp, по бокам по 9,1dp, так что скругления
# остаются внутри и щит читается целиком.
$markRatio = 0.55

# На сколько опущен знак относительно центра холста, в миллиметрах.
# Для пересчёта: 1 dp = 1/160 дюйма, 1 дюйм = 25,4 мм.
# Те же 0,15875 мм на dp, что и в комментарии к строке ниже.
$shieldDropMm = 0.5
$mmPerDp = 25.4 / 160.0

function ConvertFrom-SvgPath([string]$d) {
    # Разбор подмножества SVG pathData: M, L, Q, Z. GDI+ квадратичных
    # кривых не умеет, поэтому Q пересчитывается в кубическую:
    # контрольные точки C1 = P0 + 2/3*(C - P0), C2 = P2 + 2/3*(C - P2).
    # Для квадратичной Безье это точное преобразование, а не приближение.
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate

    $tokens = [regex]::Matches($d, '([MLQZmlqz])|(-?\d*\.?\d+)')
    $i = 0
    $x = 0.0; $y = 0.0
    $opened = $false

    while ($i -lt $tokens.Count) {
        $cmd = $tokens[$i].Groups[1].Value
        if ($cmd) { $i++ }

        switch ($cmd) {
            'M' {
                $x = [double]$tokens[$i].Value; $y = [double]$tokens[$i + 1].Value
                $i += 2
                if ($opened) { $path.StartFigure() }
                $opened = $true
            }
            'L' {
                $nx = [double]$tokens[$i].Value; $ny = [double]$tokens[$i + 1].Value
                $i += 2
                $path.AddLine([float]$x, [float]$y, [float]$nx, [float]$ny)
                $x = $nx; $y = $ny
            }
            'Q' {
                $cx = [double]$tokens[$i].Value; $cy = [double]$tokens[$i + 1].Value
                $nx = [double]$tokens[$i + 2].Value; $ny = [double]$tokens[$i + 3].Value
                $i += 4
                $c1x = $x + ($cx - $x) * 2 / 3; $c1y = $y + ($cy - $y) * 2 / 3
                $c2x = $nx + ($cx - $nx) * 2 / 3; $c2y = $ny + ($cy - $ny) * 2 / 3
                $path.AddBezier([float]$x, [float]$y, [float]$c1x, [float]$c1y,
                                [float]$c2x, [float]$c2y, [float]$nx, [float]$ny)
                $x = $nx; $y = $ny
            }
            'Z' {
                $path.CloseFigure()
            }
            default {
                # Пропущенные координаты без команды: продолжаем прежнюю
                $nx = [double]$tokens[$i].Value; $ny = [double]$tokens[$i + 1].Value
                $i += 2
                $path.AddLine([float]$x, [float]$y, [float]$nx, [float]$ny)
                $x = $nx; $y = $ny
            }
        }
    }
    return $path
}

function New-MarkMatrix([double]$cx, [double]$cy, [double]$size) {
    # Единый масштаб по обеим осям: иначе щит растянется по ширине.
    # Элементы Matrix в .NET только для чтения, поэтому задаются
    # конструктором: m11, m12, m21, m22, dx, dy.
    $s = $size / $vpH
    return New-Object System.Drawing.Drawing2D.Matrix(
        [float]$s, [float]0, [float]0, [float]$s,
        [float]($cx - $s * $vpCX), [float]($cy - $s * $vpCY))
}

function Invoke-DrawMark([int]$side, [bool]$withBackground) {
    $bmp = New-Object System.Drawing.Bitmap $side, $side, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    if ($withBackground) {
        $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.PointF 0, 0),
            (New-Object System.Drawing.PointF ([float]$side), ([float]$side)),
            $clrBgDark, $clrBgLight)
        $g.FillRectangle($bg, 0, 0, $side, $side)
        $bg.Dispose()
    }

    $markSize = $side * $markRatio
    $cx = $side / 2.0

    # Знак опущен на полмиллиметра ниже центра — так же, как щит на кнопке
    # подключения сдвинут на 2 мм. Величина задана в миллиметрах, поэтому
    # пересчитывается в пиксели через размер холста.
    #
    # Холст у двух наборов разный: legacy-иконка это 48dp целиком, а
    # foreground у adaptive icon — холст 108dp, из которого маска показывает
    # только центральные 72. Без поправки на это legacy сдвинулся бы втрое
    # сильнее, чем нужно.
    #
    # Один dp = 1/160 дюйма = 25,4/160 мм.
    $canvasDp = if ($withBackground) { 48 } else { 108 }
    $dropPx = $side * ($shieldDropMm / ($canvasDp * $mmPerDp))
    $cy = $side / 2.0 + $dropPx

    $shield = ConvertFrom-SvgPath $shieldPath
    $shield.Transform((New-MarkMatrix $cx $cy $markSize))

    $arrow = ConvertFrom-SvgPath $arrowPath
    $arrow.Transform((New-MarkMatrix $cx $cy $markSize))

    # тень: тот же силуэт, сдвинутый вниз
    $shadow = $shield.Clone()
    $shift = New-Object System.Drawing.Drawing2D.Matrix
    $shift.Translate([float]($side * 0.014), [float]0)
    $shadow.Transform($shift)
    $shadowBrush = New-Object System.Drawing.SolidBrush $clrShadow
    $g.FillPath($shadowBrush, $shadow)

    # щит. Заливка сплошная: зелёный — это фон, сам щит белый. Раньше он был
    # белым с голубым подмесом (#DEE8FF), на зелёном фоне это читалось как
    # грязный.
    $shieldBrush = New-Object System.Drawing.SolidBrush $clrShield
    $g.FillPath($shieldBrush, $shield)

    # стрелка вверх
    $arrowBrush = New-Object System.Drawing.SolidBrush $clrArrow
    $g.FillPath($arrowBrush, $arrow)

    $shadow.Dispose(); $shield.Dispose(); $arrow.Dispose()
    $arrowBrush.Dispose(); $shieldBrush.Dispose(); $shadowBrush.Dispose()
    $g.Dispose()
    return $bmp
}

function Save-Bmp($bmp, [string]$path) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$legacy = [ordered]@{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
$fore   = [ordered]@{ 'mdpi' = 108; 'hdpi' = 162; 'xhdpi' = 216; 'xxhdpi' = 324; 'xxxhdpi' = 432 }

Write-Host "Иконки «Гоппер VPN» -> $ResDir"

foreach ($k in $legacy.Keys) {
    $side = $legacy[$k]
    $b = Invoke-DrawMark $side $true
    $p = Join-Path $ResDir "mipmap-$k\ic_launcher.png"
    Save-Bmp $b $p
    Write-Host ("  mipmap-{0}/ic_launcher.png  {1}x{1}" -f $k, $side)
}

foreach ($k in $fore.Keys) {
    $side = $fore[$k]
    $b = Invoke-DrawMark $side $false
    $p = Join-Path $ResDir "mipmap-$k\ic_launcher_foreground.png"
    Save-Bmp $b $p
    Write-Host ("  mipmap-{0}/ic_launcher_foreground.png  {1}x{1}" -f $k, $side)
}

$prev = Invoke-DrawMark 512 $true
Save-Bmp $prev (Join-Path $PSScriptRoot 'icon-preview.png')
Write-Host "  icon-preview.png  512x512"
Write-Host "Готово."