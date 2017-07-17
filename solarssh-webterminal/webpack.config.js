const HtmlWebpackPlugin = require('html-webpack-plugin');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');
const webpack = require('webpack');
const path = require('path');

const devtool = 'source-map'; // cheap-module-eval-source-map

const config = {
  entry: './src/index.js',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'app.js',
    sourceMapFilename: '[file].map'
  },
  devtool: devtool,
  devServer: {
    contentBase: path.join(__dirname, "dist"),
    compress: false,
    port: 9000
  },
  module: {
    rules: [
      {
      	test: /\.js$/,
      	exclude: /(node_modules|bower_components)/,
      	use: {
          loader: 'babel-loader',
          options: {
            babelrc: false,
            presets: [
              ['env', {
                targets: {
                  browsers: ['> 5%'],
                  safari: '10.1',
                  node: 'current',
                },
                modules: false,
                useBuiltIns: true,
                debug: true,
              }],
            ],
            plugins: [
//              require('babel-plugin-transform-runtime'),
            ]
          }
        }
      },
      {test: /\.css$/, use: 'file-loader?name=css/[name].[ext]'},
      {test: /\.(gif|jpg|png)$/, use: 'file-loader?name=assets/[name].[ext]'},
    ]
  },
  plugins: [
    new UglifyJSPlugin({
      uglifyOptions: {
        mangle: {
          safari10: true,
        },
      },
      sourceMap: !!devtool,
    }),
    new HtmlWebpackPlugin({template: './src/index.html'}),
  ]
};

module.exports = config;
