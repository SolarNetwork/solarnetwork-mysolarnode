const HtmlWebpackPlugin = require("html-webpack-plugin");
const webpack = require("webpack");
const path = require("path");

const devtool = "source-map"; // cheap-module-eval-source-map

const config = {
  entry: "./src/index.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "app.js",
    sourceMapFilename: "[file].map",
  },
  devtool: devtool,
  devServer: {
    compress: false,
    port: 9000,
  },
  mode: "development",
  module: {
    rules: [
      {
        test: /prettify\.js/,
        use: "exports-loader?PR",
      },
      {
        test: /\.js.map$/,
        loader: "ignore-loader",
      },
      {
        test: /\.js$/,
        exclude: /(node_modules|bower_components)/,
        use: {
          loader: "babel-loader",
          options: {
            babelrc: false,
            presets: [
              [
                "@babel/preset-env",
                {
                  targets: {
                    browsers: ["> 5%"],
                    safari: "10.1",
                    node: "current",
                  },
                  modules: false,
                  useBuiltIns: "entry",
                  corejs: "3.11",
                  debug: false,
                },
              ],
            ],
          },
        },
      },
      {
        test: /\.css$/,
        use: [
          {
            loader: "style-loader",
            options: { injectType: "linkTag" },
          },
          {
            loader: "file-loader?name=css/[name].[ext]",
          },
        ],
      },
      { test: /\.(gif|jpg|png)$/, use: "file-loader?name=assets/[name].[ext]" },
    ],
  },
  plugins: [
    new webpack.DefinePlugin({
      VERSION: JSON.stringify(require("./package.json").version),
    }),
    new HtmlWebpackPlugin({ template: "./src/index.html" }),
    new webpack.IgnorePlugin({
      resourceRegExp: /^\.\/.*js.map$/,
      contextRegExp: /.*xterm\/lib\/addons/,
    }),
  ],
};

module.exports = config;
