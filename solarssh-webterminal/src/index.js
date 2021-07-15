import "dialog-polyfill/dialog-polyfill.css";
import "xterm/css/xterm.css";
import "./solarssh.css";
import "./favicon.png";

import startApp from "./solarssh.js";

if (!window.isLoaded) {
  window.addEventListener(
    "load",
    function() {
      startApp();
    },
    false
  );
} else {
  startApp();
}
