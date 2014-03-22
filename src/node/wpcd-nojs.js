var app = require('express')(),
    swig = require('swig'),
    http = require('http'),
    qs = require('querystring'),
    urlParser = require('url');

// This is where all the magic happens!
app.engine('html', swig.renderFile);

app.set('view engine', 'html');

// Swig will cache templates for you, but you can disable
// that and use Express's caching instead, if you like:
app.set('view cache', false);
// To disable Swig's cache, do the following:
swig.setDefaults({ cache: false });
// NOTE: You should always cache templates in a production environment.
// Don't leave both of these to `false` in production!

app.get('/dump.html', function (req, res) {
    var dumpUrl = urlParser.parse(req.url, true).query.url;
    if (dumpUrl === undefined) {
        res.statusCode = 400;
        res.end();
        return;
    }
    if (Array.isArray(dumpUrl)) {
        dumpUrl = dumpUrl[0];
    }
    console.log('asked to dump: ' + dumpUrl);
    http.get(
        'http://localhost:8080/dump?' + qs.stringify({ "url": dumpUrl }),
        function (handleRes) {
            if (handleRes.statusCode != 200) {
                console.log('backend responded with code ' + handleRes.statusCode);
                res.statusCode = 500;
                res.end();
                return;
            }
            var body = '';
            handleRes.setEncoding('utf8');
            handleRes.on('data', function (chunk) {
                body += chunk;
            });
            handleRes.on('end', function () {
                var json;
                try {
                    json = JSON.parse(body);
                } catch (e) {
                    console.log('backend response is bad: ' + e.message);
                    res.statusCode = 500;
                    res.end();
                }
                res.render('dump', { shkurka: dumpUrl, myakotka: json.task });
            });
        }
    ).on('error', function(e) {
        console.log('backend communication error: ' + e.message);
        res.statusCode = 503;
        res.end();
    });
});

app.listen(1337);
console.log('Application Started on http://localhost:1337/');