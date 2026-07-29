<?php
declare(strict_types=1);

$mapId = $_GET['map_id'] ?? '';
if (!is_string($mapId) || preg_match('/^[0-9]+$/D', $mapId) !== 1) {
    http_response_code(400);
    exit;
}

$matches = glob('/src/data/maps/' . $mapId . '_*X.swf', GLOB_NOSORT);
if ($matches === false || $matches === []) {
    http_response_code(404);
    exit;
}

sort($matches, SORT_STRING);
$path = $matches[0];
$size = filesize($path);
if ($size === false) {
    http_response_code(500);
    exit;
}

header('Content-Type: application/x-shockwave-flash');
header('Content-Length: ' . $size);
header('X-StarLoco-Map-Fallback: 1');
readfile($path);
