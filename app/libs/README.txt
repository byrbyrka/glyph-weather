Сюда нужно положить официальный AAR Nothing Glyph Matrix SDK.

Файл: glyph-matrix-sdk-2.0.aar  (или актуальная версия)

Где взять:
  https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
  (папка с .aar внутри репозитория)

После копирования .aar в эту папку синхронизируйте Gradle — SDK подхватится
автоматически (см. dependencies в app/build.gradle.kts).

Без этого файла проект не соберётся: классы com.nothing.ketchum.* берутся из него.
