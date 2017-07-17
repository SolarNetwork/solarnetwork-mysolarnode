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
                  browsers: ['last 2 versions'],
                  node: 'current',
                },
                modules: false,
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
      sourceMap: !!devtool,
    }),
    new HtmlWebpackPlugin({template: './src/index.html'}),
  ]
};

module.exports = config;
