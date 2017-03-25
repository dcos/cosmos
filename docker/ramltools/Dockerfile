FROM node:4.8
RUN npm install -g raml2html && \
    npm install -g https://github.com/stoplightio/api-spec-converter
COPY raml2swagger.js /usr/local/bin/raml2swagger
ENTRYPOINT ["nodejs"]
