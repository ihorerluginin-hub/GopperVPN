# Генерация иконок «Гоппер VPN».
#   powershell -ExecutionPolicy Bypass -File .\make_icon.ps1
# Требуется Windows PowerShell 5.1 + System.Drawing. Больше ничего.

param(
    [string]$ResDir = (Join-Path $PSScriptRoot 'app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Доли от ширины/высоты знака. Щит — силуэт, стрелка — «прыжок».
$shield = @(
    @(-0.50, -0.44), @(0.50, -0.44), @(0.50, 0.06), @(0.44, 0.24),
    @( 0.32,  0.38), @(0.16,  0.47), @(0.00,  0.50),
    @(-0.16,  0.47), @(-0.32, 0.38), @(-0.44, 0.24), @(-0.50, 0.06)
)
$arrow = @(
    @( 0.000, -0.26), @( 0.190, -0.02), @( 0.085, -0.02),
    @( 0.085,  0.24), @(-0.085, 0.24), @(-0.085, -0.02), @(-0.190, -0.02)
)

function New-MarkPoints([double]$cx, [double]$cy, [double]$w, [double]$h, $pts, [double]$dx) {
    $out = New-Object 'System.Drawing.PointF[]' $pts.Count
    for ($i = 0; $i -lt $pts.Count; $i++) {
        $out[$i] = New-Object System.Drawing.PointF `
            ([float]($cx + $pts[$i][0] * $w + $dx)), `
            ([float]($cy + $pts[$i][1] * $h))
    }
    return ,$out
}

function New-GradientBrush([double]$w, [double]$h) {
    New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF 0, 0),
        (New-Object System.Drawing.PointF ([float]$w), ([float]$h)),
        [System.Drawing.Color]::FromArgb(255, 31, 111, 235),
        [System.Drawing.Color]::FromArgb(255, 124, 58, 237)
    )
}

function Invoke-DrawMark([int]$side, [bool]$withBackground) {
    $bmp = New-Object System.Drawing.Bitmap $side, $side, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    if ($withBackground) {
        $g.FillRectangle((New-GradientBrush $side $side), 0, 0, $side, $side)
    }

    $markW = $side * 0.62
    $markH = $side * 0.66
    $cx    = $side / 2.0
    $cy    = $side / 2.0

    # тень: тот же силуэт, сдвинутый вниз
    $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(55, 0, 0, 0))
    $g.FillPolygon($shadowBrush, (New-MarkPoints $cx $cy $markW $markH $shield ([double]$side * 0.014)))

    # щит
    $shieldBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF 0, ([float]($cy - $markH / 2))),
        (New-Object System.Drawing.PointF 0, ([float]($cy + $markH / 2))),
        [System.Drawing.Color]::White,
        [System.Drawing.Color]::FromArgb(255, 222, 232, 255)
    )
    $g.FillPolygon($shieldBrush, (New-MarkPoints $cx $cy $markW $markH $shield 0))

    # стрелка вверх
    $arrowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 37, 99, 235))
    $g.FillPolygon($arrowBrush, (New-MarkPoints $cx $cy $markW $markH $arrow 0))

    # три линии «прыжка» под знаком, каскадом к центру
    $trail = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(150, 255, 255, 255)), ([float]($side * 0.026))
    $trail.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $trail.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
    $yTrail = [float]($cy + $markH * 0.545)
    $g.DrawLine($trail, [float]($cx - $markW * 0.30), $yTrail, [float]($cx - $markW * 0.46), $yTrail)
    $g.DrawLine($trail, [float]($cx - $markW * 0.22), [float]($yTrail + $side * 0.052), [float]($cx - $markW * 0.36), [float]($yTrail + $side * 0.052))
    $g.DrawLine($trail, [float]($cx + $markW * 0.30), $yTrail, [float]($cx + $markW * 0.46), $yTrail)
    $g.DrawLine($trail, [float]($cx + $markW * 0.22), [float]($yTrail + $side * 0.052), [float]($cx + $markW * 0.36), [float]($yTrail + $side * 0.052))

    $trail.Dispose(); $arrowBrush.Dispose(); $shieldBrush.Dispose(); $shadowBrush.Dispose()
    $g.Dispose()
    return $bmp
}

function Save-Bmp($bmp, [string]$path) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("  {0,-42} {1}x{2}" -f (Split-Path $path -Leaf | Split-Path -Leaf), (Get-Item $path).Length, '')
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
