// Asset loader — strips near-gray background pixels (matching Android logic)
const Assets = {
  images: {},
  _pending: 0,
  _done: 0,
  onReady: null,

  load(names) {
    this._pending = names.length;
    names.forEach(name => {
      const img = new Image();
      img.onload = () => this._onLoad(name, img);
      img.onerror = () => { this._done++; this._check(); };
      img.src = `assets/${name}.png`;
      this.images[name] = img;
    });
  },

  _onLoad(name, img) {
    // Remove near-gray background the same way the Android version does
    const canvas = document.createElement('canvas');
    canvas.width = img.width;
    canvas.height = img.height;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0);
    const data = ctx.getImageData(0, 0, img.width, img.height);
    const d = data.data;
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i], g = d[i+1], b = d[i+2];
      const avg = (r + g + b) / 3;
      const maxDiff = Math.max(Math.abs(r-g), Math.abs(g-b), Math.abs(r-b));
      if (maxDiff <= 45 && avg >= 60 && avg <= 240) {
        d[i+3] = 0; // transparent
      }
    }
    ctx.putImageData(data, 0, 0);
    const clean = new Image();
    clean.src = canvas.toDataURL();
    this.images[name] = clean;
    this._done++;
    this._check();
  },

  _check() {
    if (this._done >= this._pending && this.onReady) this.onReady();
  },

  get(name) { return this.images[name]; }
};
