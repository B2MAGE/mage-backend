# Contact sheets for visual review of the actual engine-captured PNGs.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$reviewRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.local/quality-review'))
$thumbnailRoot = Join-Path $reviewRoot 'thumbnails'
$files = @(Get-ChildItem -LiteralPath $thumbnailRoot -Filter '*.png' | Sort-Object Name)
$font = New-Object Drawing.Font('Segoe UI', 10)
try {
    for ($page = 0; $page -lt [Math]::Ceiling($files.Count / 25); $page++) {
        $bitmap = New-Object Drawing.Bitmap(1200, 815)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([Drawing.Color]::FromArgb(14, 15, 19))
            for ($slot = 0; $slot -lt 25; $slot++) {
                $index = $page * 25 + $slot
                if ($index -ge $files.Count) { break }
                $source = [Drawing.Image]::FromFile($files[$index].FullName)
                try {
                    $x = ($slot % 5) * 240
                    $y = [Math]::Floor($slot / 5) * 163
                    $graphics.DrawImage($source, [int]$x, [int]$y, 240, 135)
                    $graphics.DrawString(('Scene ' + $files[$index].BaseName), $font, [Drawing.Brushes]::White, [single]($x + 8), [single]($y + 140))
                } finally { $source.Dispose() }
            }
            $destination = Join-Path $reviewRoot ('contact-sheet-' + ($page + 1) + '.png')
            $bitmap.Save($destination, [Drawing.Imaging.ImageFormat]::Png)
            Write-Output $destination
        } finally { $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $font.Dispose() }
