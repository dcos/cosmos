var transformer = require('/usr/local/lib/node_modules/api-spec-transformer');

var ramlToSwagger = new transformer.Converter(
    transformer.Formats.RAML10,
    transformer.Formats.SWAGGER
);

/* Based on how this script get called:
 * process.argv[0] == nodejs
 * process.argv[1] == raml2swagger
 * process.argv[2] == <the path to the RAML file>
 */
var source = process.argv[2];

ramlToSwagger.loadFile(source, function(err) {
  if (err) {
    process.stderr.write(err.stack);
    exit(1);
  }

  ramlToSwagger.convert('yaml')
    .then(function(convertedData) {
      // convertedData is swagger YAML string
      process.stdout.write(convertedData);
      process.stdout.write('\n');
    })
  .catch(function(err){
    process.stderr.write(err.stack);
    exit(1);
  });
});
