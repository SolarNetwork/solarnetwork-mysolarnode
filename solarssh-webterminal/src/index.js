import 'dialog-polyfill/dialog-polyfill.css';
import 'xterm/dist/xterm.css';
import './solarssh.css';

import startApp from './solarssh.js';

if ( !window.isLoaded ) {
	window.addEventListener("load", function() {
		startApp();
	}, false);
} else {
	startApp();
}
