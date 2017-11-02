export class GoogleAnalytics {
    constructor($timeout,$interval, $location, ArtifactoryState, FooterDao) {

        this.$location = $location;
        this.$interval = $interval;
        this.$timeout = $timeout;
        this.ArtifactoryState = ArtifactoryState;
        this.footerDao = FooterDao;


        this.footerDao.get().then(() => {
            this.allowGA = (this.footerDao.getInfo().isAol && !this.footerDao.getInfo().isDedicatedAol) || _.includes(this.footerDao.getInfo().buildNumber, 'SNAPSHOT');
        });

        this.artifactsPageCounter = '';
    }


    _setUpGA() {
        if (this.allowGA) {

            // setup timeout settings
            this.GA = {
                active: true
            };

             let uaCode = (_.includes(this.footerDao.getInfo().buildNumber, 'SNAPSHOT')) ? 'UA-87840116-1' : 'UA-87840116-2';

            (function(i,s,o,g,r,a,m) {i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
                        (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
                    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;if (m) m.parentNode.insertBefore(a,m);
            })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

            window.ga('create', uaCode, 'auto');
            window.ga('set', 'dataSource', 'Artifactory UI');

            this.ArtifactoryState.setState('gaTrackPage', () => {
                let url = this.$location.$$absUrl;
                if (url.slice(-2) == '//') return;

                // ignore entry to tree without path (user will redirect to first result in tree)
                if (url.match(/(#.+)/) && url.match(/(#.+)/)[1] == '#/artifacts/browse/tree/General/') return;


                // * * * * * * Calculating time inside artifacts page * * * * * * //

                if (url.match(/#\/artifacts/)) {    // if in artifacts page
                    let currentTime = Date.now();

                    if (this.artifactsPageCounter != '') {
                        this.timeOnArtifactsPage = currentTime - this.artifactsPageCounter;
                    }
                    this.artifactsPageCounter = currentTime;

                } else {    // if not artifacts page
                    if (this.artifactsPageCounter) {
                        this.timeOnArtifactsPage = Date.now() - this.artifactsPageCounter;  // calculate time in case of leaving artifacts page
                        delete this.artifactsPageCounter;
                    }
                }

                // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //


                // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
                //
                // Send pageview without the full path for artifacts browser page (tree and simple)
                //
                // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *

                if (url.match(/\/browse\/tree\/|browse\/simple/)) {
                    // If url location is in tree browse or simple browse ignore the path
                    // from the url set to GA. leave only the relevant part of the URL.
                    this._sendPageView(url.match(/#.*(tree|simple)\/.*?\//)[0])

                } else if (url.match(/(#.+)/)) {
                    this._sendPageView(url.match(/(#.+)/)[1])
                }
            })

            this.ArtifactoryState.getState('gaTrackPage')();
        }
    }

    _createRandomId() {

        let text = "";
        let possible = "abcdefghijklmnopqrstuvwxyz0123456789";

        for( var i=0; i < 10; i++ )
            text += possible.charAt(Math.floor(Math.random() * possible.length));

        return text;

    }

    _generateDimensions() {

        let randomId = this._createRandomId();
        let currentTime = new Date().getTime();

        let sessionId = "S" + randomId + "-" + currentTime;
        let interactionId = "I" + randomId + "-" + currentTime;

        let dimensions = {
            timestamp: currentTime,
            sessionId: sessionId,
            interactionId: interactionId
        }

        return dimensions;

    }


    _sendPageView(pageUrl, hitType = 'pageview') {

        let dimensions = this._generateDimensions();

        window.ga('set', {
            page: pageUrl,
            dimension4: dimensions.timestamp,
            dimension5: dimensions.interactionId,
            dimension6: dimensions.sessionId
        });


        if (this.timeOnArtifactsPage) {
            window.ga('send', {
                hitType: hitType,
                metric1: this.timeOnArtifactsPage,
                dimension4: dimensions.timestamp,
                dimension5: dimensions.interactionId,
                dimension6: dimensions.sessionId
            });
            delete this.timeOnArtifactsPage;
            return;
        }

        window.ga('send', {
            hitType: hitType
        });

    }

    trackEvent(eventCategory, eventAction, eventLabel = '', eventValue = null, dimension1 = '', dimension2 = '', dimension3 = '') {

        this._generateDimensions();

        if (this.allowGA && window.ga) {
            // Track google analytics event
            // ga('send', 'event', [eventCategory], [eventAction], [eventLabel], [eventValue], [fieldsObject]);
            // More on this here: https://developers.google.com/analytics/devguides/collection/analyticsjs/events

            window.ga('send', {
                hitType: 'event',
                eventCategory: eventCategory,
                eventAction: eventAction,
                eventLabel: eventLabel,
                eventValue: eventValue,
                dimension1: dimension1,
                dimension2: dimension2,
                dimension3: dimension3
            });
        }
    }

    _resetCounter() {
        this.GA._idleSecondsCounter = 0;
    }
}