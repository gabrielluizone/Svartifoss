# Bundled typefaces

The TTF files in `mobile/src/main/res/font/` and `wear/src/main/res/font/` are bundled typefaces,
not app source code. The 50 families added in the current catalog expansion were downloaded from
the official [Google Fonts repository](https://github.com/google/fonts) and are distributed
unmodified under the SIL Open Font License, version 1.1.

Each family has its complete upstream `OFL.txt` in the matching directory below this folder. The
same copyright notices and upstream links are also present in the phone app's open-source license
dialog (`mobile/src/main/res/raw/notices.xml`). The two APKs use byte-identical copies of each
font; `BundledFontLicenseTest` checks that this remains true.

The license permits embedding and redistribution with the app. It does not permit selling a font
by itself, and the font license remains separate from the GPL-3.0 license that covers Svartifoss
source code.
