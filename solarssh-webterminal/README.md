# SolarSSH Web Terminal

This project contains a webapp that connects to a [SolarSSH][solarssh]
server and initiates a browser-based terminal session to a SolarNode.

Here's a demo of how this webapp can be used to establish a shell terminal on
a SolarNode:

![demo](docs/solarssh-demo-shell.gif)

The webapp also sports a handy button for launching a new window with
the SolarNode Setup GUI, using the SolarSSH HTTP proxy:

![httpproxy](docs/solarssh-demo-http-proxy.png)

This webapp can be loaded from anywhere, even from your local computer if you
download the source. It uses the [SolarSSH][solarssh-api] and [SolarNet][solarnet-api]
APIs to accomplish everything.

# Building

NPM is used to build the app. Run `npm run build` to build (or `yarn run build`),
then the `dist/index.html` file can be opened in your browser.

# URL parameters

You can pass some URL parameters to the webapp to modify a few things:

<table>
	<tr>
		<th><code>nodeId</code></th>
		<td>This one is pretty much necessary. Sets the ID of the node you want to control.</td>
	</tr>
	<tr>
		<th><code>cols</code></th>
		<td>The number of columns to use in the terminal. Defaults to <code>100</code>.</td>
	</tr>
	<tr>
		<th><code>lines</code></th>
		<td>The number of lines (rows) to use in the terminal. Defaults to <code>24</code>. Classic!</td>
	</tr>
</table>

For example, to control node ID 123 using a 80x48 terminal, you'd use a URL like

	solarssh.html?nodeId=123&cols=80&lines=48

# Development

This app uses the [SolarNetwork Core library][sn-api-core] for working with the SolarNet API,
and provides a good demonstration of how classes like [AuthorizationV2Builder][auth-builder]
and [NodeInstructionUrlHelper][node-instr-urlhelper] can be used to easily work with 
SolarNetwork. The [d3-request][d3-request] library provides simple XHR support.

  [solarssh]: https://github.com/SolarNetwork/solarnetwork-mysolarnode/tree/develop/solarssh
  [solarssh-api]: https://github.com/SolarNetwork/solarnetwork/wiki/SolarSSH-API
  [solarnet-api]: https://github.com/SolarNetwork/solarnetwork/wiki/API-Developer-Guide
  [sn-api-core]: https://github.com/SolarNetwork/sn-api-core-js
  [auth-builder]: https://github.com/SolarNetwork/sn-api-core-js/blob/master/src/net/authV2.js
  [node-instr-urlhelper]: https://github.com/SolarNetwork/sn-api-core-js/blob/master/src/net/nodeInstructionUrlHelperMixin.js
  [d3-request]: https://github.com/d3/d3-request
