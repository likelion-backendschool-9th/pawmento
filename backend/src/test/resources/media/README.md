# 미디어 테스트 파일

외부 사진·녹음 없이 FFmpeg lavfi 단색·440Hz 사인파·무음을 생성한 fixture다. JPEG/PNG/WebP, WebM/Ogg의 Opus, M4A/AAC, MP3, 미지원 ALAC, 음성으로 위장한 MP4 영상과 300초 경계·301초 음성을 포함한다. oriented.jpg는 생성 JPEG에 방향 6과 가짜 개인 메타데이터를 넣은 TIFF/EXIF 블록을 추가했다. too-many-pixels.png는 8000×6000 단색을 zlib로 압축해 4천만 픽셀 제한을 검사한다.

예: `ffmpeg -f lavfi -i color=c=green:s=80x40 -frames:v 1 sample.png`, `ffmpeg -f lavfi -i sine=frequency=440:duration=0.4 -c:a libopus sample.ogg`.
