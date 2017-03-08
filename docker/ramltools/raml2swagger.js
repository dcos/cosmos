var transformer = require('/usr/local/lib/node_modules/api-spec-transformer');

var ramlToSwagger = new transformer.Converter(
    transformer.Formats.RAML10,
    transformer.Formats.SWAGGER
);

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
