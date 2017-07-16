'use strict';

import { Configuration,
	NetAuthorizationV2Builder as AuthorizationV2Builder,
	NetEnvironment as Environment,
	NodeInstructionUrlHelper,
	InstructionState,
	netUrlQuery } from 'solarnetwork-core';
import { select, selectAll } from 'd3-selection';
import { json as jsonRequest } from 'd3-request';
import dialogPolyfill from 'dialog-polyfill';
import Terminal from 'xterm';

Terminal.loadAddon('attach');

const forceEnv = {
	// uncomment these for production
	tls: true,

	/* comment out these for production
	debug: true,
	tls: false,
	host: 'solarnetworkdev.net:8680'
	nodeId : 167,
	solarSshHost: 'solarnetworkdev.net:8080',
	solarSshPath: '/solarssh',
	solarSshTls: false,
	*/
};

const ansiEscapes = {
	color: {
		bright: {
			gray:	'\x1B[30;1m',
			green:	'\x1B[32;1m',
			red:	'\x1B[31;1m',
			yellow:	'\x1B[33;1m',
			white:	'\x1B[37;1m'
		},
	},
	reset:	'\x1B[0m',
};

var app;

/**
 * SolarSSH web app, supporting a SSH terminal (via xterm.js) and integration with the HTTP proxy.
 *
 * @class
 */
var solarSshApp = function(nodeUrlHelper, options) {
	var self = { version : '0.2.0' };
	var authBuilder = new AuthorizationV2Builder(null, nodeUrlHelper.environment);
	var config = (options || {});
	var env = nodeUrlHelper.environment;
	var sshCredentialsDialog;
	var terminal;

	var termSettings = {
		cols: (config.cols || 100),
		lines: (config.lines || 24),
	};

	var session;
	var socket;
	var socketState = 0;
	var setupGuiWindow;
	var sshCredentials;

	var dialogCancelled = false;

	/**
	 * Get/set the terminal column size.
	 *
	 * Defaults to 100.
	 *
	 * @param {Number} [value] if provided, set the column size to this value
	 * @return if invoked as a getter, the current column size value; otherwise this object
	 */
	function cols(value) {
		if ( !arguments.length ) return termSettings.cols;
		if ( value > 0 ) {
			termSettings.cols = value;
		}
		return self;
	}

	/**
	 * Get/set the terminal lines size.
	 *
	 * Defaults to 24.
	 *
	 * @param {Number} [value] if provided, set the line size to this value
	 * @return if invoked as a getter, the current line size value; otherwise this object
	 */
	function lines(value) {
		if ( !arguments.length ) return termSettings.lines;
		if ( value > 0 ) {
			termSettings.lines = value;
		}
		return self;
	}

	/**
	 * Get/set the SSH credentials HTML <dialog> element to use.
	 *
	 * The <dialog> must include a <form method="dialog"> that includes a username text input field
	 * and a password input field. If the form is submitted with a "login" value, the credentials
	 * will be used to establish a SSH terminal session. Otherwise no SSH terminal session will be
	 * opened (but the HTTP proxy functionality may still be used).
	 *
	 * @param {Element} [value] if provided, set the SSH credential dialog element to this value
	 * @return if invoked as a getter, the current SSH credential dialog element value; otherwise this object
	 */
	function sshCredentialsDialog(value) {
		if ( !arguments.length ) return sshCredentialsDialog;
		sshCredentialsDialog = value;
		if ( value ) {
			value.addEventListener('close', handleSshCredentials);
			value.addEventListener('cancel', handleSshCredentialsCancel);
		}
		return self;
	}

	function reset() {
		if ( setupGuiWindow ) {
			setupGuiWindow = undefined;
		}
		resetWebSocket();
		session = undefined;
		if ( terminal ) {
			terminal.clear();
			termWriteGreeting();
		}
		sshCredentials = undefined;
		enableSubmit(true);
	}

	function resetWebSocket() {
		if ( socket ) {
			if ( terminal ) {
				terminal.detach(socket);
			}
			socket.close();
			socket = undefined;
		}
		socketState = 0;
	}

	function hostUrl() {
		return ('http' +(env.solarSshTls === true ? 's' : '') +'://' +env.solarSshHost);
	}

	function baseUrl() {
		return (hostUrl() +env.solarSshPath +'/api/v1/ssh');
	}

	function webSocketUrl() {
		return ('ws' +(env.solarSshTls === true ? 's' : '') +'://'
			+env.solarSshHost +env.solarSshPath +'/ssh');
	}

	function setupGuiUrl() {
		return ('http' +(env.solarSshTls === true ? 's' : '') +'://'
			+env.solarSshHost +env.solarSshPath +'/nodeproxy/' +session.sessionId +'/');
	}

	function enableSubmit(value, withoutCascade) {
		select('#connect').property('disabled', !value);
		if ( value && !withoutCascade ) {
			enableSetupGui(false);
			enableEnd(false);
		}
	}

	function enableSetupGui(value) {
		select('#setup-gui').property('disabled', !value);
	}

	function enableEnd(value) {
		select('#end').property('disabled', !value);
	}

	function termEscapedText(esc, text, withoutReset) {
		var value = esc + text;
		if ( !withoutReset ) {
			value += ansiEscapes.reset;
		}
		return value;
	}

	function termWriteEscapedText(esc, text, newline, withoutReset) {
		var value = termEscapedText(esc, text, withoutReset);
		if ( newline ) {
			terminal.writeln(value);
		} else {
			terminal.write(value);
		}
	}

	function termWriteBrightGreen(text, newline) {
		termWriteEscapedText(ansiEscapes.color.bright.green, text, newline);
	}

	function termWriteBrightRed(text, newline) {
		termWriteEscapedText(ansiEscapes.color.bright.red, text, newline);
	}

	function termWriteGreeting() {
		terminal.writeln('Hello from '
			+termEscapedText(ansiEscapes.color.bright.yellow, 'Solar')
			+termEscapedText(ansiEscapes.color.bright.gray, 'SSH')
			+'!');
	}

	function termWriteSuccess(withoutNewline) {
		termWriteBrightGreen('SUCCESS', !withoutNewline);
	}

	function termWriteFailed(withoutNewline) {
		termWriteBrightRed('FAILED', !withoutNewline);
	}

	function executeWithPreSignedAuthorization(method, url, builder) {
		builder.snDate(true).date(new Date());
		var req = jsonRequest(url);
		req.on('beforesend', function(request) {
			request.setRequestHeader('X-SN-Date', builder.requestDateHeaderValue);
			request.setRequestHeader('X-SN-PreSignedAuthorization', builder.buildWithSavedKey());
		});
		console.log('Requesting %s %s', method, url);
		req.send(method);
		return req;
	}

	function connect() {
		authBuilder.tokenId = select('input[name=token]').property('value');
		authBuilder.saveSigningKey(select('input[name=secret]').property('value'));
		enableSubmit(false);
		console.log('connect using token %s', authBuilder.tokenId);
		requestSshCredentials();
	}

	function createSession() {
		var url = baseUrl() + '/session/new?nodeId=' +nodeUrlHelper.nodeId;
		authBuilder.reset().method('GET').url(nodeUrlHelper.viewPendingInstructionsUrl());
		terminal.write('Requesting new SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authBuilder)
			.on('load', handleCreateSession)
			.on('error', function(xhr) {
				console.error('Failed to create session: %s', xhr.responseText);
				enableSubmit(true);
				termWriteFailed();
				termWriteBrightRed('Failed to get request new SSH session: ' +xhr.responseText, true);
			});
	}

	function handleCreateSession(json) {
		if ( !(json.success && json.data && json.data.sessionId) ) {
			console.error('Failed to create session: %s', JSON.stringify(json));
			enableSubmit(true);
			return;
		}
		termWriteSuccess();
		console.log('Created session %s', json.data.sessionId);
		session = json.data;
		startSession();
	}

	function startSession() {
		var url = baseUrl() + '/session/' +session.sessionId +'/start';
		authBuilder.reset().method('POST').contentType('application/x-www-form-urlencoded')
			.url(nodeUrlHelper.queueInstructionUrl('StartRemoteSsh', [
				{name: 'host', value: session.host},
				{name: 'user', value: session.sessionId},
				{name: 'port', value: session.port},
				{name: 'rport', value: session.reversePort }
			]));
		terminal.write('Requesting SolarNode to establish remote SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authBuilder)
			.on('load', handleStartSession)
			.on('error', function(xhr) {
				console.error('Failed to start session: %s', xhr.responseText);
				enableSubmit(true);
			});
	}

	function handleStartSession(json) {
		if ( !(json.success && json.data && json.data.sessionId) ) {
			console.error('Failed to start session: %s', JSON.stringify(json));
			enableSubmit(true);
			return;
		}
		termWriteSuccess();
		console.log('Started session %s', json.data.sessionId);
		session = json.data;
		enableEnd(true);
		waitForStartRemoteSsh();
	}

	function stopSession() {
		if ( socket && terminal ) {
			resetWebSocket();
			terminal.writeln('');
		}
		var url = baseUrl() + '/session/' +session.sessionId +'/stop';
		authBuilder.reset().method('POST').contentType('application/x-www-form-urlencoded')
			.url(nodeUrlHelper.queueInstructionUrl('StopRemoteSsh', [
				{name: 'host', value: session.host},
				{name: 'user', value: session.sessionId},
				{name: 'port', value: session.port},
				{name: 'rport', value: session.reversePort }
			]));
		terminal.write('Requesting SolarNode to stop remote SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authBuilder)
			.on('load', handleStopSession)
			.on('error', function(xhr) {
				console.error('Failed to stop session: %s', xhr.responseText);
				reset();
			});
	}

	function handleStopSession(json) {
		if ( !json.success ) {
			console.error('Failed to stop session: %s', JSON.stringify(json));
			termWriteFailed();
		} else {
			console.log('Stopped session %s', session.sessionId);
			termWriteSuccess();
		}
		setTimeout(reset, 1000);
	}

	function waitForStartRemoteSsh() {
		terminal.write('Waiting for SolarNode to establish remote SSH session...');
		var url = nodeUrlHelper.viewInstructionUrl(session.startInstructionId);
		function executeQuery() {
			if ( !session ) {
				return;
			}
			authBuilder.reset().snDate(true).url(url);
			var req = jsonRequest(url);
			req.on('beforesend', function(request) {
				request.setRequestHeader('X-SN-Date', authBuilder.requestDateHeaderValue);
				request.setRequestHeader('Authorization', authBuilder.buildWithSavedKey());
			}).on('load', function(json) {
				if ( !(json.success && json.data && json.data.state) ) {
					console.error('Failed to query StartRemoteSsh instruction %d: %s', session.startInstructionId, JSON.stringify(json));
					enableSubmit(true);
					return;
				}
				var state = json.data.state;
				if ( 'Completed' === state ) {
					// off to the races!
					terminal.write(' ');
					termWriteSuccess();
					enableSetupGui(true);
					terminal.writeln('Use the '
						+termEscapedText(ansiEscapes.color.bright.yellow, 'Setup')
						+' button to view the SolarNode setup GUI.');
					if ( sshCredentials ) {
						connectWebSocket();
					} else {
						terminal.writeln('Use the '
							+termEscapedText(ansiEscapes.color.bright.yellow, 'Connect')
							+' button to connect via SSH.');
						enableSubmit(true, true); // re-enable just the Connect button to establish SSH later
					}
				} else if ( 'Declined' === state ) {
					// bummer!
					terminal.write(' ');
					termWriteFailed();
					setTimeout(stopSession, 1000);
				} else {
					// still waiting... try again in a little bit
					terminal.write('.');
					setTimeout(executeQuery, 15000);
				}
			})
			.on('error', function(xhr) {
				console.error('Failed to query StartRemoteSsh instruction %d: %s', session.startInstructionId, xhr.responseText);
				enableSubmit(true);
				terminal.write(' ');
				termWriteFailed();
				termWriteBrightRed('Failed to get SolarNode remote SSH session start status: ' +xhr.responseText, true);
			})
			.send('GET');
		}
		executeQuery();
	}

	function requestSshCredentials() {
		dialogCancelled = false;
		sshCredentialsDialog.showModal();
	}

	function handleSshCredentialsCancel(event) {
		dialogCancelled = true;
	}

	function handleSshCredentials(event) {
		var dialog = select(sshCredentialsDialog);
		var usernameInput = dialog.select('input[type=text]')
		if ( sshCredentialsDialog.returnValue === 'login' && !dialogCancelled ) {
			var username = usernameInput.property('value');
			var password = dialog.select('input[type=password]').property('value');
			sshCredentials = {username: username, password: password};
		} else {
			sshCredentials = undefined;
		}

		// reset credentials form to clear
		usernameInput.node().form.reset();

		// if did not provide credentials, we will not connect the websocket
		if ( !session ) {
			createSession();
		} else if ( sshCredentials && !socket ) {
			// re-use existing session
			terminal.writeln('');
			connectWebSocket();
		}
	}

	function connectWebSocket() {
		terminal.write('Attaching to SSH session... ');
		var url = webSocketUrl() +'?sessionId=' +session.sessionId;
		socket = new WebSocket(url, 'solarssh');
		socket.onopen = webSocketOpen;
		socket.onmessage = webSocketMessage;
		socket.onerror = webSocketError;
		socket.onclose = webSocketClose;
	}

	function webSocketOpen(event) {
		authBuilder.reset().method('GET').url(nodeUrlHelper.viewNodeMetadataUrl());
		var msg = {
			cmd: "attach-ssh",
			data: {
				'authorization': authBuilder.buildWithSavedKey(),
				'authorization-date': authorization.requestDate.getTime(),
				'username': (sshCredentials ? sshCredentials.username : ''),
				'password' : (sshCredentials ? sshCredentials.password : ''),
				'term' : 'xterm',
				'cols' : cols(),
				'lines' : lines(),
			}
		};

		// clear saved credentials
		sshCredentials = undefined;

		socket.send(JSON.stringify(msg));
	}

	function webSocketClose(event) {
		console.log('ws close event: code = %d; reason = %s', event.code, event.reason);
		resetWebSocket();
		if ( event.code === 1000 ) {
			// CLOSE_NORMAL
			if ( terminal ) {
				terminal.writeln('');
				terminal.writeln('Use the '
					+termEscapedText(ansiEscapes.color.bright.yellow, 'Connect')
					+' button to reconnect via SSH.');
				terminal.writeln('The '
					+termEscapedText(ansiEscapes.color.bright.yellow, 'Setup')
					+' button can still be used to view the SolarNode setup GUI.');
			}
		} else if ( event.code === 4000 ) {
			// AUTHENTICATION_FAILURE
			if ( terminal ) {
                termWriteFailed();
				if ( event.reason ) {
					termWriteBrightRed(event.reason, true);
				}
			}
		} else if ( terminal ) {
			terminal.writeln('Connection closed: ' +event.reason);
		}
		enableSubmit(true, true); // re-enable just the Connect button
	}

	function webSocketError(event) {
		console.log('ws error event: %s', JSON.stringify(event));
	}

	function webSocketMessage(event) {
		var msg;
		if ( socketState !== 0 ) {
			return;
		}
		msg = JSON.parse(event.data);
		if ( msg.success ) {
			termWriteSuccess();
			socketState = 1;
			terminal.attach(socket);
		} else {
			termWriteFailed();
			termWriteBrightRed('Failed to attach to SSH session: ' +event.data, true);
			socket.close();
		}
	}

	function launchSetupGui() {
		if ( setupGuiWindow && !setupGuiWindow.closed ) {
			setupGuiWindow.location = setupGuiUrl();
		} else {
			setupGuiWindow = window.open(setupGuiUrl());
		}
	}

	/**
	 * Initialize the app.
	 *
	 * This will initialize the terminal and prepare the app for use.
	 *
	 * @return this object
	 */
	function start() {
		terminal = new Terminal({
			cols: termSettings.cols,
			rows: termSettings.lines,
			tabStopWidth: 4
		});
		terminal.open(document.getElementById('terminal'), true);
		termWriteGreeting();
		return self;
	}

	/**
	 * Stop the app.
	 *
	 * This will stop any active session and reset the app for re-use.
	 *
	 * @return this object
	 */
	function stop() {
		if ( session ) {
			stopSession();
		} else {
			reset();
		}
		return self;
	}

	function init() {
		select('#connect').on('click', connect);
		select('#setup-gui').on('click', launchSetupGui);
		select('#end').on('click', stop);
		return Object.defineProperties(self, {
			// property getter/setter functions

			cols: { value: cols },
			lines: { value: lines },
			sshCredentialsDialog: { value: sshCredentialsDialog },

			// action methods

			start: { value: start },
			stop: { value: stop },
		});
	}

	return init();
};

function setupUI(env) {
	selectAll('.node-id').text(env.nodeId);
}

export default function startApp(env) {
	if ( !env ) {
		env = new Environment({
			tls: true,
			solarSshHost: 'ssh.solarnetwork.net:8443',
			solarSshPath: '',
			solarSshTls: true,
		});
	}

	var config = new Configuration(Object.assign({nodeId:251}, netUrlQuery.urlQueryParse(window.location.search)));

	setupUI(config);

	var sshCredDialog = document.getElementById('ssh-credentials-dialog');
	dialogPolyfill.registerDialog(sshCredDialog);

	var urlHelper = new NodeInstructionUrlHelper(env);
	urlHelper.nodeId = config.nodeId;

	app = solarSshApp(urlHelper, config)
		.sshCredentialsDialog(sshCredDialog)
		.start();

	window.onbeforeunload = function() {
		app.stop();
	}

	return app;
}
