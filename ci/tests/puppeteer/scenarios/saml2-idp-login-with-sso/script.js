const puppeteer = require('puppeteer');
const path = require('path');
const cas = require('../../cas.js');

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    console.log("Establishing SSO session...");
    await cas.goto(page, "https://localhost:8443/cas/login");
    await cas.loginWith(page);
    
    await cas.goto(page, "http://localhost:9443/simplesaml/module.php/core/authenticate.php?as=default-sp");
    await page.waitForTimeout(3000);
    await cas.screenshot(page);
    await page.waitForSelector('#table_with_attributes', {visible: true});
    await cas.assertInnerTextContains(page, "#content p", "status page of SimpleSAMLphp");
    await cas.assertVisibility(page, "#table_with_attributes");

    let authData = JSON.parse(await cas.innerHTML(page, "details pre"));
    console.log(authData);
    
    let entityId = "https://httpbin.org/shibboleth";
    let url = "https://localhost:8443/cas/idp/profile/SAML2/Unsolicited/SSO";
    url += `?providerId=${entityId}`;
    url += "&target=https%3A%2F%2Flocalhost%3A8443%2Fcas%2Flogin";
    await cas.goto(page, url);
    await page.waitForTimeout(1000);
    await cas.assertInnerText(page, "#content h2", "Application Not Authorized to Use CAS");
    await cas.removeDirectory(path.join(__dirname, '/saml-md'));
    await browser.close();
})();

