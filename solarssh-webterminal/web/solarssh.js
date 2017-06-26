/**
 * @require d3 3.0
 * @require queue 1.0
 * @require solarnetwork-d3 0.2.0
 * @require xterm 2.7
 */

(function(window) {
'use strict';

var devEnv = {
	// comment out these for production
	debug: true,
	tls: false,
	host: 'solarnetworkdev.net:8680'
};

var ansiEscapes = {
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

var solarSshApp = function(nodeUrlHelper, options) {
	var self = { version : '0.1.0' };
	var helper = sn.net.securityHelper();
	var config = (options || {});
	var sshCredentialsDialog;
	var terminal;

	var session;
	var socket;
	var socketState = 0;
	var setupGuiWindow;
	var sshCredentials;

	function sshCredentialsDialog(value) {
		if ( !arguments.length ) return sshCredentialsDialog;
		sshCredentialsDialog = value;
		if ( value ) {
			value.addEventListener('close', handleSshCredentials);
		}
		return self;
	}

	function reset() {
		if ( setupGuiWindow ) {
			setupGuiWindow = undefined;
		}
		if ( socket ) {
			if ( terminal ) {
				terminal.detach(socket);
			}
			socket.close();
			socket = undefined;
		}
		socketState = 0;
		session = undefined;
		if ( terminal ) {
			terminal.clear();
			termWriteGreeting();
		}
		sshCredentials = undefined;
		enableSubmit(true);
	}

	function hostURL() {
		return ('http' +(config.solarSshTls === true ? 's' : '') +'://' +config.solarSshHost);
	}

	function baseURL() {
		return (hostURL() +config.solarSshPath +'/api/v1/ssh');
	}

	function webSocketURL() {
		return ('ws' +(config.solarSshTls === true ? 's' : '') +'://' +config.solarSshHost +config.solarSshPath +'/ssh');
	}

	function setupGuiURL() {
		return ('http' +(config.solarSshTls === true ? 's' : '') +'://' +config.solarSshHost +config.solarSshPath +'/nodeproxy/' +session.sessionId);
	}

	function enableSubmit(value, withoutCascade) {
		d3.select('#connect').property('disabled', !value);
		if ( value && !withoutCascade ) {
			enableSetupGui(false);
			enableEnd(false);
		}
	}

	function enableSetupGui(value) {
		d3.select('#setup-gui').property('disabled', !value);
	}

	function enableEnd(value) {
		d3.select('#end').property('disabled', !value);
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

	function executeWithPreSignedAuthorization(method, url, authorization) {
		var req = d3.json(url);
		req.on('beforesend', function(request) {
			request.setRequestHeader('X-SN-Date', authorization.dateHeader);
			request.setRequestHeader('X-SN-PreSignedAuthorization', authorization.header);
		});
		console.log('Requesting %s %s', method, url);
		req.send(method);
		return req;
	}

	function connect() {
		helper.token(d3.select('input[name=token]').property('value'));
		helper.secret(d3.select('input[name=secret]').property('value'));
		enableSubmit(false);
		console.log('connect using token %s', helper.token());
		requestSshCredentials();
	}

	function createSession() {
		var url = baseURL() + '/session/new?nodeId=' +nodeUrlHelper.nodeId;
		var authorization = helper.computeAuthorization(
			nodeUrlHelper.viewPendingInstructionsURL(),
			'GET',
			undefined,
			undefined,
			new Date()
		);
		terminal.write('Requesting new SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authorization)
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
		var url = baseURL() + '/session/' +session.sessionId +'/start';
		var authorization = helper.computeAuthorization(
			nodeUrlHelper.queueInstructionURL('StartRemoteSsh', [
				{name: 'host', value: session.host},
				{name: 'user', value: session.sessionId},
				{name: 'port', value: session.port},
				{name: 'rport', value: session.reversePort }
			]),
			'POST',
			undefined,
			'application/x-www-form-urlencoded',
			new Date()
		);
		terminal.write('Requesting SolarNode to establish remote SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authorization)
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
			terminal.detach(socket);
			terminal.writeln('');
			socket.close();
			socket = undefined;
		}
		var url = baseURL() + '/session/' +session.sessionId +'/stop';
		var authorization = helper.computeAuthorization(
			nodeUrlHelper.queueInstructionURL('StopRemoteSsh', [
				{name: 'host', value: session.host},
				{name: 'user', value: session.sessionId},
				{name: 'port', value: session.port},
				{name: 'rport', value: session.reversePort }
			]),
			'POST',
			undefined,
			'application/x-www-form-urlencoded',
			new Date()
		);
		terminal.write('Requesting SolarNode to stop remote SSH session... ');
		return executeWithPreSignedAuthorization('GET', url, authorization)
			.on('load', handleStopSession)
			.on('error', function(xhr) {
				console.error('Failed to stop session: %s', xhr.responseText);
				enableSubmit(true);
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
		var url = nodeUrlHelper.viewInstruction(session.startInstructionId);
		function executeQuery() {
			if ( !session ) {
				return;
			}
			helper.json(url)
				.on('load', function(json) {
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
						}
					} else if ( 'Declined' === state ) {
						// bummer!
						terminal.write(' ');
						termWriteFailed();
						enableSubmit(true);
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
		sshCredentialsDialog.showModal();
	}

	function handleSshCredentials(event) {
		if ( sshCredentialsDialog.returnValue === 'login' ) {
			var dialog = d3.select(sshCredentialsDialog);
			var username = dialog.select('input[type=text]').property('value');
			var password = dialog.select('input[type=password]').property('value');
			sshCredentials = {username: username, password: password};
		}
		// if did not provide credentials, we will not connect the websocket
		createSession();
	}

	function connectWebSocket() {
		terminal.write('Attaching to SSH session... ');
		var url = webSocketURL() +'?sessionId=' +session.sessionId;
		socket = new WebSocket(url, 'solarssh');
		socket.onopen = webSocketOpen;
		socket.onmessage = webSocketMessage;
		socket.onerror = webSocketError;
		socket.onclose = webSocketClose;
	}

	function webSocketOpen(event) {
		var authorization = helper.computeAuthorization(
			nodeUrlHelper.viewNodeMetadataURL(),
			'GET',
			undefined,
			undefined,
			new Date()
		);
		var dialog = d3.select(sshCredentialsDialog);
		var username = dialog.select('input[type=text]').property('value');
		var password = dialog.select('input[type=password]').property('value');
		var msg = {
			cmd: "attach-ssh",
			data: {
				'authorization': authorization.header,
				'authorization-date': authorization.date.getTime(),
				'username': username,
				'password' : password,
			}
		};

		socket.send(JSON.stringify(msg));
	}

	function webSocketClose(event) {
		console.log('ws close event: %s', JSON.stringify(event));
		socket = undefined;
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
		setupGuiWindow = window.open(setupGuiURL());
	}

	function start() {
		terminal = new Terminal();
		terminal.open(document.getElementById('terminal'), true);
		termWriteGreeting();
		return self;
	}

	function stop() {
		if ( session ) {
			stopSession();
		} else {
			reset();
		}
		return self;
	}

	function init() {
		d3.select('#connect').on('click', connect);
		d3.select('#setup-gui').on('click', launchSetupGui);
		d3.select('#end').on('click', stop);
		return Object.defineProperties(self, {
			start: { value: start },
			stop: { value: stop },
			sshCredentialsDialog: { value: sshCredentialsDialog },
		});
	}

	return init();
};

function setupUI(env) {
	d3.selectAll('.node-id').text(env.nodeId);
}

function startApp(env) {
	if ( !env ) {
		env = sn.util.copy(devEnv, sn.util.copy(sn.env, {
			nodeId : 167,
			solarSshHost: 'solarnetworkdev.net:8080',
			solarSshPath: '/solarssh',
			solarSshTls: false,
		}));
	}

	setupUI(env);

	var sshCredDialog = document.getElementById('ssh-credentials-dialog');
	dialogPolyfill.registerDialog(sshCredDialog);

	app = solarSshApp(sn.api.node.nodeUrlHelper(env.nodeId, env), env)
		.sshCredentialsDialog(sshCredDialog)
		.start();

	window.onbeforeunload = function() {
		app.stop();
	}

	return app;
}

sn.runtime.solarSshApp = startApp;

}(window));
