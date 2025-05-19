# SolarSSH

> :warning: This project has been moved to the
> [SolarNetwork/solarssh-server](https://github.com/SolarNetwork/solarssh-server)
> repo. No further changes will be made here.

This project is a cloud-based server that enables accessing SolarNode devices
via a SSH tunnel. It works with the [SolarNode System SSH][system.ssh] plugin to
establish a SSH connection between the SolarNode and this SolarSSH server and
then allows web browser-based terminal access through the SSH connection.

Here's a demo of how SolarSSH can be used to establish a shell terminal on
a SolarNode using the [SolarSSH Web Terminal][solarssh-webterm] webapp.

![demo](src/docs/solarssh-demo-shell.gif)

# Quick networking overview

Here's a diagram that shows how the various components involved with SolarSSH
initiate network connections, and the protocols used:

![netconn](src/docs/solarssh-network-connections.png)

As you can see, no component initiates a network connection _to_ SolarNode.
Instead, all traffic is tunneled to SolarNode over the SSH connection it
_initiates_ to SolarSSH.

# SolarNode HTTP proxy

In addition to providing a way to access a shell terminal on SolarNode, SolarSSH
provides a reverse HTTP proxy for the SolarNode web server. This means the
SolarNode setup GUI can be accessed as well.

![httpproxy](src/docs/solarssh-demo-http-proxy.png)

# SolarSSH deployment

SolarSSH has been designed to be easily deployable on your own infrastructure.
It uses the public SolarNetwork API to communicate with SolarNetwork, and thus
can be deployed just about anywhere. See the [deployment guide][solarssh-deploy]
for more information.

# SolarSSH API

If you want to build an app that uses SolarSSH (like the [SolarSSH Web
Terminal][solarssh-webterm] webapp) the [SolarSSH API][solarssh-api] is
documented on the SolarNetwork wiki.


 [system.ssh]: https://github.com/SolarNetwork/solarnetwork-node/tree/develop/net.solarnetwork.node.system.ssh
 [solarssh-webterm]: https://github.com/SolarNetwork/solarnetwork-mysolarnode/tree/develop/solarssh-webterminal
 [solarssh-api]: https://github.com/SolarNetwork/solarnetwork/wiki/SolarSSH-API
 [solarssh-deploy]: https://github.com/SolarNetwork/solarnetwork/wiki/SolarSSH-Deployment-Guide
