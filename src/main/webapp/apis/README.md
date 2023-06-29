# MetroHero API Docs
Generates documentation for the MetroHero API.

## Installation
1. `nvm install 12.22.12`
2. `npm install -g spectacle-docs-cheerio-version-fix`
3. `npm install -g --dev coffeescript`

## Usage
1. `spectacle -l images/android/android-launchericon-512-512.png swagger.json`
2. Move files and directories:
   * `public/javascripts` => `javascripts`
   * `public/stylesheets` => `stylesheets`
   * `public/index.html` => `index.html`
3. Delete now-orphaned `public` directory; there should be nothing left in it.
4. Find and replace the following in index.html:
   * `stylesheets/` => `apis/stylesheets/`
   * `javascripts/` => `apis/javascripts/`
   * make sure `images/android/android-launchericon-512-512.png` is still there
   * make sure Google Analytics stuff is still there
   * any other changes that don't seem relevant to your update
