[CmdletBinding()]
param([switch]$SkipRender, [switch]$Open, [switch]$CompareBefore)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $SkipRender) {
    & (Join-Path $PSScriptRoot 'gradle.ps1') testDebugUnitTest --tests app.ritela.HomeScreenTest --rerun-tasks
}

$directory = Join-Path $projectRoot 'app/build/reports/screenshots'
$labels = [ordered]@{
    'home-light' = 'Главная · светлая тема'
    'home-dark' = 'Главная · тёмная тема'
    'home-active' = 'Месячные идут'
    'home-recorded' = 'Заполненная история'
    'home-active-dark' = 'Месячные идут · тёмная тема'
    'home-recorded-dark' = 'История · тёмная тема'
    'home-narrow-large-text' = 'Узкий экран · шрифт 200%'
    'period-entry' = 'Запись дат'
    'period-finish' = 'Выбор окончания'
    'period-edit' = 'Изменение дат'
    'period-delete' = 'Подтверждение удаления'
    'calendar-recorded' = 'Календарь · записанные даты'
    'calendar-forecast' = 'Календарь · прогноз начала'
    'calendar-dark' = 'Календарь · тёмная тема'
    'calendar-narrow-medium-text' = 'Календарь · узкий экран · шрифт 130%'
    'calendar-narrow-large-text' = 'Календарь · узкий экран · шрифт 200%'
    'home-english' = 'Home · English'
    'period-entry-english' = 'Log period · English'
    'calendar-english' = 'Calendar · English'
    'home-forecast' = 'Главная · новый стиль · прогноз'
    'home-forecast-dark' = 'Главная · новый стиль · тёмная тема'
    'home-forecast-english' = 'Cycle estimate · English'
    'home-forecast-dark-english' = 'Cycle estimate · English · dark'
    'home-forecast-narrow-english' = 'Cycle estimate · English · narrow · 200%'
    'home-forecast-narrow-action-english' = 'Log action · English · narrow · 200%'
    'settings' = 'Настройки · светлая тема'
    'settings-english' = 'Settings · English'
    'settings-dark' = 'Настройки · тёмная тема'
    'settings-dark-english' = 'Settings · Dark · English'
    'home-insights' = 'Графики цикла · светлая тема'
    'home-insights-dark' = 'Графики цикла · тёмная тема'
    'home-insights-english' = 'Cycle charts · English'
    'home-insights-dark-english' = 'Cycle charts · English · dark'
    'home-help' = 'Карточки помощи'
    'home-help-dark' = 'Карточки помощи · тёмная тема'
    'home-help-english' = 'Help cards · English'
    'home-help-dark-english' = 'Help cards · English · dark'
    'day-entry' = 'Отметки дня'
    'day-entry-english' = 'Day log · English'
    'day-entry-narrow-dark-english' = 'Day log · English · narrow · 200% · dark'
    'home-day-log' = 'Сегодня · отметки и графики'
    'help-medication' = 'Справочник лекарств · ограничения и дозы'
    'backup-password-english' = 'Backup password · English'
    'calendar-month-picker' = 'Выбор месяца и года'
    'period-range' = 'Выбор диапазона · граница месяца'
    'period-occupied' = 'Запись дат · занятые дни'
    'calendar-day-details' = 'Подробности выбранного дня'
    'calendar-day-forecast-details' = 'Подробности дня · прогноз'
    'calendar-day-details-dark' = 'Подробности дня · тёмная тема'
    'calendar-free-scroll' = 'Календарь · свободная прокрутка'
    'home-sparklines' = 'Ваш цикл · выбор измерения'
}
$cards = foreach ($name in $labels.Keys) {
    $path = Join-Path $directory "$name.png"
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing render: $path. Run preview-ui.ps1 without -SkipRender." }
    $label = [System.Net.WebUtility]::HtmlEncode($labels[$name])
    $rendered = (Get-Item -LiteralPath $path).LastWriteTime.ToString('yyyy-MM-dd HH:mm')
    "<figure><figcaption>$label <small>$rendered</small></figcaption><a href='$name.png'><img src='$name.png' alt='$label' loading='lazy'></a></figure>"
}
$html = @"
<!doctype html>
<html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ritela · просмотр интерфейса</title>
<style>
body{margin:0;padding:32px;background:#f3f3ed;color:#202823;font:16px system-ui,sans-serif}
h1{font-size:28px;font-weight:600}p{max-width:760px;line-height:1.6;color:#535f57}
main{display:flex;flex-wrap:wrap;gap:32px;align-items:flex-start}
figure{margin:0;width:300px}figcaption{margin:0 0 12px;font-weight:600}small{display:block;font-weight:400;color:#535f57;font-size:12px;margin-top:4px}
img{width:100%;height:auto;border-radius:16px;box-shadow:0 4px 24px #20282314}
a:focus-visible{outline:3px solid #315d50} @media(max-width:380px){body{padding:16px}figure{width:100%}}
</style>
<h1>Ritela</h1><p>Снимки Android-интерфейса на тестовых данных. Нажмите на экран, чтобы открыть его в полном размере. Здесь нельзя нажимать кнопки приложения: для этого нужен эмулятор.</p>
<main>$($cards -join [Environment]::NewLine)</main></html>
"@
$output = Join-Path $directory 'index.html'
[IO.File]::WriteAllText($output, $html, [Text.UTF8Encoding]::new($false))
Write-Output "UI gallery: $output"
if ($CompareBefore) {
    $comparison = foreach ($name in @('home-forecast', 'home-insights', 'home-help', 'settings', 'calendar-forecast', 'calendar-dark', 'period-entry')) {
        $before = Join-Path $projectRoot "app/build/reports/polish-before/$name.png"
        if (-not (Test-Path -LiteralPath $before)) { throw "Missing baseline: $before" }
        $label = [System.Net.WebUtility]::HtmlEncode($labels[$name])
        "<section><h2>$label</h2><div><figure><figcaption>До</figcaption><img src='../polish-before/$name.png'></figure><figure><figcaption>После</figcaption><img src='$name.png'></figure></div></section>"
    }
    $comparisonHtml = @"
<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ritela · до и после</title><style>
body{margin:24px;background:#F8F4EF;color:#2D2231;font:16px system-ui,sans-serif}
section{margin:32px 0}div{display:flex;flex-wrap:wrap;gap:24px}figure{margin:0;width:320px}img{width:100%;height:auto}figcaption{margin-bottom:12px}
</style><h1>Ritela · до и после</h1><p>Синтетические данные. Исходные снимки сохранены перед доработкой.</p>
$($comparison -join [Environment]::NewLine)</html>
"@
    $output = Join-Path $directory 'comparison.html'
    [IO.File]::WriteAllText($output, $comparisonHtml, [Text.UTF8Encoding]::new($false))
    Write-Output "UI comparison: $output"
}
if ($Open) { Start-Process -FilePath $output }
