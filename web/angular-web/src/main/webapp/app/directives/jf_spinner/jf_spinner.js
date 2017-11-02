import EVENTS from "../../constants/artifacts_events.constants";
class jfSpinnerController {

    constructor($timeout, $scope, $state, JFrogEventBus, $element) {
        this.$scope = $scope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$element = $element;
        this.show = false;
        this.count = 0;
        this.JFrogEventBus = JFrogEventBus;
        this.intervalPromise = null;
        this.JFrogEventBus.registerOnScope(this.$scope, EVENTS.SHOW_SPINNER, (domain) => {
            this.showSpinner(domain);
        });
        this.JFrogEventBus.registerOnScope(this.$scope, EVENTS.HIDE_SPINNER, () => {
            this.hideSpinner()
        });
    }

    showSpinner(domain) {
        if ((!domain && this.domain === 'body' && this.$state.current.name === 'login') ||
                (!domain && this.domain === 'content' && this.$state.current.name !== 'login') ||
                (this.domain === domain)) {

            this.count++;
            this.show = true;
            this.lastShowTime = (new Date()).getTime();
        }
    }

    hideSpinner() {

        let doHide = () => {
            this.count--;
            if (this.count<0) this.count = 0;
            if (this.count === 0) {
                this.show = false;
            }
        }

        if (!this.lastShowTime) doHide();
        else {
            let timeOn = (new Date()).getTime() - this.lastShowTime;
            if (timeOn > 600) doHide();
            else {
                let addTime = 600 - timeOn;
                this.$timeout(()=>{
                    doHide();
                },addTime);
            }
        }

    }

    isModalOpen() {
        return ($('.modal').length > 0) ? true : false;
    }


}

export function jfSpinner() {

    return {
        restrict: 'E',
        scope: {
            domain: '@'
        },
        controller: jfSpinnerController,
        controllerAs: 'jfSpinner',
        templateUrl: 'directives/jf_spinner/jf_spinner.html',
        bindToController: true
    };
}
