# Play 그래픽 이미지(1024x500) 생성.
#
# ImageMagick 이 없어서 .NET System.Drawing 으로 직접 그린다.
# (PATH 의 `convert` 는 ImageMagick 이 아니라 윈도우 내장 디스크 변환 도구다 — 실행하면 안 된다)
#
# 색과 도형은 런처 아이콘에서 그대로 가져온다:
#   배경 #1F4D33 (ic_launcher_background.xml)
#   전경 #F7F3E8 (ic_launcher_foreground.xml)
#   텐트 path: M54,28 L86,74 L22,74 Z / 입구 M54,46 L67,74 L41,74 Z / 지면 M27,79 L81,79 (108 캔버스)

param(
    [Parameter(Mandatory = $true)][string]$OutPath,
    [string]$Title = '캠핑각',
    # ⚠️ 건수를 넣지 않는다. 공공데이터가 매일 갱신돼 숫자가 계속 어긋나는데,
    #    그림에 박힌 숫자는 고치는 비용이 가장 비싼 자리다 —
    #    이미지를 다시 만들고 스토어에 다시 올려 심사를 다시 받아야 한다.
    [string]$Subtitle = '전국 캠핑장을 조건으로 찾는다'
)

Add-Type -AssemblyName System.Drawing

$W = 1024; $H = 500
$bg      = [System.Drawing.ColorTranslator]::FromHtml('#1F4D33')
$fg      = [System.Drawing.ColorTranslator]::FromHtml('#F7F3E8')
# 배경 산: 배경보다 살짝 밝게. 알파를 쓰지 않는다 — Play 아이콘은 알파 금지고,
# 피처 그래픽도 불투명하게 두는 편이 어떤 배경에서도 같아 보인다.
$hill    = [System.Drawing.ColorTranslator]::FromHtml('#25583B')

$bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g   = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear($bg)

# --- 배경 산 두 개 ---
$hillBrush = New-Object System.Drawing.SolidBrush($hill)
$g.FillPolygon($hillBrush, @(
    (New-Object System.Drawing.Point(760, 230)),
    (New-Object System.Drawing.Point(1120, 500)),
    (New-Object System.Drawing.Point(400, 500))))
$g.FillPolygon($hillBrush, @(
    (New-Object System.Drawing.Point(200, 330)),
    (New-Object System.Drawing.Point(520, 500)),
    (New-Object System.Drawing.Point(-120, 500))))

# --- 텐트 (아이콘 벡터를 그대로 확대) ---
# 108 캔버스 기준 좌표를 scale 배 키우고 (ox, oy) 로 옮긴다.
$scale = 3.2
$tentTopY = 85              # 텐트 꼭대기가 놓일 y
$ox = 512 - (54 * $scale)   # 텐트 중심(x=54)을 가로 중앙에
$oy = $tentTopY - (28 * $scale)

function P([double]$x, [double]$y) {
    New-Object System.Drawing.PointF(($ox + $x * $scale), ($oy + $y * $scale))
}

# 바깥 삼각형과 입구를 한 path 에 넣고 evenOdd 로 뚫는다.
# 입구를 배경색으로 덮지 않는 이유는 아이콘과 같다 — 배경이 바뀌어도 입구가 따라간다.
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
$path.AddPolygon(@((P 54 28), (P 86 74), (P 22 74)))
$path.AddPolygon(@((P 54 46), (P 67 74), (P 41 74)))
$fgBrush = New-Object System.Drawing.SolidBrush($fg)
$g.FillPath($fgBrush, $path)

# 지면
$pen = New-Object System.Drawing.Pen($fg, (5 * $scale))
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($pen, (P 27 79), (P 81 79))

# --- 텍스트 ---
# 한글이 있는 폰트를 고른다. 없는 폰트를 지정하면 .NET 이 조용히 대체 폰트를 써서
# 네모(두부)로 렌더링되는 일이 있다 — 설치된 것 중에서 고른다.
$installed = (New-Object System.Drawing.Text.InstalledFontCollection).Families | ForEach-Object { $_.Name }
$titleFontName = @('Pretendard', 'Malgun Gothic', '맑은 고딕', 'Noto Sans KR', 'Gulim') |
    Where-Object { $installed -contains $_ } | Select-Object -First 1
if (-not $titleFontName) { throw '한글 폰트를 찾지 못했습니다.' }
Write-Host "폰트: $titleFontName"

$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = [System.Drawing.StringAlignment]::Center

$titleFont = New-Object System.Drawing.Font($titleFontName, 66, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString($Title, $titleFont, $fgBrush, (New-Object System.Drawing.PointF(512, 296)), $fmt)

# 부제는 흰색을 조금 죽인다. 같은 밝기면 제목과 경쟁한다.
$subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(205, 222, 211))
$subFont  = New-Object System.Drawing.Font($titleFontName, 27, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString($Subtitle, $subFont, $subBrush, (New-Object System.Drawing.PointF(512, 392)), $fmt)

$g.Dispose()
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "생성: $OutPath ($([math]::Round((Get-Item $OutPath).Length / 1KB, 1)) KB)"
