import EVENTS   from '../../../constants/artifacts_events.constants';
export class BrowsersController {
    constructor($timeout, $scope, $stateParams, $state, TreeBrowserDao, JFrogEventBus, hotkeys, ArtifactoryState) {
        this.JFrogEventBus = JFrogEventBus;
        this.stateParams = $stateParams;
        this.state = $state;
        this.treeBrowserDao = TreeBrowserDao;
        this.artifactoryState = ArtifactoryState;
        this.compactFolders = TreeBrowserDao.getCompactFolders();
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.hotkeys = hotkeys;
        this._setupKeyHints();
        this.JFrogEventBus.registerOnScope(this.$scope, EVENTS.TREE_NODE_SELECT, node => this.selectedNode = node);

        let activeFilter = this.artifactoryState.getState('activeFilter');
        this.activeFilter = activeFilter ? true : false;
        this.searchText = activeFilter || '';

        this.JFrogEventBus.registerOnScope(this.$scope, EVENTS.TREE_SEARCH_RUNNING, (running) => {
            this.searchInAction = running;
        });


        this.sendWindowResizeEvent();
    }

    toggleCompactFolders() {
        this.treeBrowserDao.setCompactFolders(this.compactFolders);
        this.JFrogEventBus.dispatch(EVENTS.TREE_COMPACT, this.compactFolders);
    }

    showTreeSearch() {
        this.JFrogEventBus.dispatch(EVENTS.ACTIVATE_TREE_SEARCH);
    }

    switchBrowser(browser) {
        this.artifactoryState.setState('activeFilter', this.activeFilter ? this.searchText : undefined);

        // Reclicking simple browser when we are already in simple browser - go to root
        if (browser === 'simple' && this.stateParams.browser === 'simple') {
            let repo = this.selectedNode.data.getRoot();
            // Make sure roots are visible:
            this.artifactoryState.setState('tree_touched', false);
            // Use forceLoad as a Date to ensure state transition even if it's the same as before
            this.state.go(this.state.current.name, {browser: browser, artifact: repo.fullpath, forceLoad: new Date()});
        }
        else if (browser === 'tree' && this.stateParams.browser === 'tree') {
            this.JFrogEventBus.dispatch(EVENTS.TREE_COLLAPSE_ALL);
        }
        else if (browser != this.stateParams.browser) {
            this.state.go(this.state.current.name, {browser: browser});
            this.treeBrowserDao.invalidateRoots();
            // console.log(" from "+this.stateParams.browser+" to "+browser);
        }
    }

    _setupKeyHints() {
        this.hotkeys.bindTo(this.$scope).add({
            combo: 'Enter',
            description: 'Select node'
        }).add({
            combo: 'Esc',
            description: 'Cancel search / deselect node'
        }).add({
            combo: 'Down',
            description: 'Navigate down in tree / in search results'
        }).add({
            combo: 'Up',
            description: 'Navigate up in tree / in search results'
        }).add({
            combo: 'Right',
            description: 'Expand folder'
        }).add({
            combo: 'Left',
            description: 'Collapse folder'
        });
    }

    clearFilter() {
        this.JFrogEventBus.dispatch(EVENTS.TREE_SEARCH_CANCEL);
    }


    compressEmptyFoldersLabelIsOverflowing(){
        let header = $('.tree-browser-header');
        return header.length > 0 && header.width() < 424;
    }

    sendWindowResizeEvent() {
        let resizeEvent = document.createEvent("Event");
        resizeEvent.initEvent("resize", false, true);
        let doSafeResize = () => {
            try {
                window.dispatchEvent(new Event('resize'));
            }
            catch (e) {
                window.dispatchEvent(resizeEvent);
            }
        }

        this.$timeout(()=>{
            doSafeResize();
            this.$timeout(()=>{
                doSafeResize();
            })
        })
    }
}
