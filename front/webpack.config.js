const path = require('path');
const HtmlWebPackPlugin = require("html-webpack-plugin");

const page = 'openingsTrainer';

module.exports = {
  entry: `./src/${page}.js`,
  output: {
    filename: `${page}.js`,
    path: path.resolve(__dirname, 'dist'),
  },
  module: {
    rules: [
      /*{
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader"
        }
      },*/
      {
        test: /\.html$/,
        use: [
          {
            loader: "html-loader"
          }
        ]
      },
      {
        test: /\.css$/,
        use: [
          {
            loader: 'css-loader'
          }
        ]
      }
    ]
  },
  plugins: [
    new HtmlWebPackPlugin({
      template: `./src/${page}.html`,
      filename: `./${page}.html`, // automatically prefixed with output path
      //hash: true
      inject: false
    })
  ],
  devtool: 'eval-source-map'
};
