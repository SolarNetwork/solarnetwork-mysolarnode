const HtmlWebpackPlugin = require("html-webpack-plugin");
const path = require("path");

const devtool = "source-map"; // cheap-module-eval-source-map

const config = {
  entry: "./src/index.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "app.js",
    sourceMapFilename: "[file].map"
  },
  devtool: devtool,
  devServer: {
    contentBase: path.join(__dirname, "dist"),
    compress: false,
    port: 9000
  },
  mode: "development",
  module: {
    rules: [
      {
        test: /prettify\.js/,
        use: "exports-loader?PR"
      },
      {
        test: /\.js.map$/,
        loader: "ignore-loader"
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
                    node: "current"
                  },
                  modules: false,
                  corejs: "2",
                  useBuiltIns: "entry"
                }
              ]
            ]
          }
        }
      },
      {
        test: /\.css$/,
        use: [
          {
            loader: "style-loader/url",
            options: { sourceMap: false }
          },
          {
            loader: "file-loader?name=css/[name].[ext]"
          }
        ]
      },
      { test: /\.(gif|jpg|png)$/, use: "file-loader?name=assets/[name].[ext]" }
    ]
  },
  plugins: [new HtmlWebpackPlugin({ template: "./src/index.html" })]
};

module.exports = config;
