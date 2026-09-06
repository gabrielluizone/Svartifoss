# Bundled typefaces

The TTF files in `mobile/src/main/res/font/` and `wear/src/main/res/font/` are bundled typefaces,
not app source code. As of this catalog, that's 121 families, distributed under the SIL Open Font
License, version 1.1 - except Special Elite, which is Apache License 2.0. Most were downloaded
unmodified from the official [Google Fonts repository](https://github.com/google/fonts); a few
(Inter, Roboto Mono, Silkscreen, Courier Prime, Google Sans, Google Sans Flex) come from their own
dedicated upstream repositories instead, which is what their `OFL.txt`'s copyright line names. About
forty carry a small, deliberate vertical-metrics correction (centring the font's own flat capital
letters in its line box) - each one documented in its own `licenses/<name>/MODIFICATIONS.txt`,
pinned by `BundledFontMetricsTest`, so "distributed unmodified" is not true catalog-wide.

Each family has its complete upstream `OFL.txt` (or, for Special Elite, `LICENSE.txt`) in the
matching directory below this folder. The same copyright notices and upstream links are also
present in the phone app's open-source license dialog (`mobile/src/main/res/raw/notices.xml`) -
`BundledFontAttributionTest` checks that every bundled family's license text is quoted there, so a
newly added font that never reaches this dialog fails the build rather than shipping silently
uncredited, the way roughly seventy older families in this catalog once did. The two APKs use
byte-identical copies of each font (`BundledFontLicenseTest` pins this for the named batch it
tracks; `BundledFontAttributionTest` covers attribution for the whole catalog).

The license permits embedding and redistribution with the app. It does not permit selling a font
by itself, and the font license remains separate from the GPL-3.0 license that covers Svartifoss
source code.
