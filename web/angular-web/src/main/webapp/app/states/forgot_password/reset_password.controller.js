const PASSWORD_CHANGED_MESSAGE = 'Password changed successfully';

export class ResetPasswordController {

    constructor($stateParams, User, $state, JFrogNotifications, JFrogEventBus, $timeout) {
        this.$stateParams = $stateParams;
        this.userService = User;
        this.$state = $state;
        this.key = $stateParams.key;
        this.artifactoryNotifications = JFrogNotifications;
        this.JFrogEventBus = JFrogEventBus;
        this.ResetPasswordForm = null;
        this.$timeout = $timeout;
        this.user = {};
        this.EVENTS = JFrogEventBus.getEventsDefinition();
    }

    resetPassword() {
        var self = this;

        this.JFrogEventBus.dispatch(this.EVENTS.FORM_SUBMITTED);

        if (this.ResetPasswordForm.$valid) {
            this.userService.validateKey(this.key).then(success, error);
        }

        function success(response) {
            if (response.data.user) {
                self.user.user = response.data.user;
                self.userService.resetPassword(self.key, self.user).then(function (response) {
                    self.artifactoryNotifications.create(response.data);
                    self.$state.go('login');
                });
            }
        }

        function error(errors) {
            if (errors.data.error) {
                self.artifactoryNotifications.create({error: errors.data.error});
            }
        }
    }

    checkMatchingPasswords() {
        this.$timeout(() => {
            if (this.ResetPasswordForm.password.$valid && this.ResetPasswordForm.repeatPassword.$valid) {
            this.JFrogEventBus.dispatch(this.EVENTS.FORM_CLEAR_FIELD_VALIDATION);
            }
        });
    }
}