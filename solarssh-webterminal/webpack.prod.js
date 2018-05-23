const Merge = require('webpack-merge');
const BaseConfig = require('./webpack.config.js');

const UglifyJSPlugin = require('uglifyjs-webpack-plugin');

const devtool = 'source-map'; // cheap-module-eval-source-map

module.exports = Merge(BaseConfig, {
	plugins: [
		new UglifyJSPlugin({
			uglifyOptions: {
				mangle: {
					safari10: true,
				},
			},
			sourceMap: !!devtool,
		}),
	]
});
