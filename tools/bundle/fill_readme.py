# README 틀의 {KEY_NOTE} 를 채워 BOM 달린 UTF-8 · CRLF 로 쓴다 (메모장에서 안 깨지게)
import sys
src, note, out = sys.argv[1:4]
s = open(src, encoding='utf-8').read().replace('{KEY_NOTE}', note)
open(out, 'w', encoding='utf-8-sig', newline='\r\n').write(s)
