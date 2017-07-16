const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');
const path = require('path');

const config = {
  entry: './src/index.js',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'app.js',
    sourceMapFilename: '[file].map'
  },
  devtool: 'source-map',//'cheap-module-eval-source-map',
  devServer: {
    contentBase: path.join(__dirname, "dist"),
    compress: false,
    port: 9000
  },
  resolve: { modules: ["node_modules", path.join(__dirname, "node_modules")] },
  resolveLoader: { modules: ["node_modules", path.join(__dirname, "node_modules")] },
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
              }],
            ],
            plugins: [
              require('babel-plugin-transform-runtime'),
              [
                "module-resolver",
                {
                  "root": [
                    "./src"
                  ],
                  "cwd": "packagejson"
                }
              ],
            ]
          }
        }
      },
      {test: /\.css$/, use: 'file-loader?name=css/[name].[ext]'},
      {test: /\.(gif|jpg|png)$/, use: 'file-loader?name=assets/[name].[ext]'},
    ]
  },
  plugins: [
    /* uri-js has Windows line endings, causing Uglify to fail :(
    new webpack.optimize.UglifyJsPlugin({
      comments: false,
      compress: {
        warnings: false
      },
      sourceMap: true,
    }),*/
    new HtmlWebpackPlugin({template: './src/index.html'}),
  ]
};

module.exports = config;
