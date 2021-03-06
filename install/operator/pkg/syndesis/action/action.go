package action

import (
	"context"

	"github.com/go-logr/logr"
	"github.com/syndesisio/syndesis/install/operator/pkg/apis/syndesis/v1beta1"
	corev1 "k8s.io/api/core/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes"
	"sigs.k8s.io/controller-runtime/pkg/client"
	logf "sigs.k8s.io/controller-runtime/pkg/log"
	"sigs.k8s.io/controller-runtime/pkg/manager"
)

const (
	replaceResourcesIfPresent = true
)

// Client is an abstraction for a k8s client
type Client struct {
	client.Client
	kubernetes.Interface
}

type baseAction struct {
	log    logr.Logger
	client client.Client
	scheme *runtime.Scheme
	api    kubernetes.Interface
	mgr    manager.Manager
}

var actionLog = logf.Log.WithName("action")

// SyndesisOperatorAction an action that will be executed by the operator
type SyndesisOperatorAction interface {
	CanExecute(syndesis *v1beta1.Syndesis) bool
	Execute(ctx context.Context, syndesis *v1beta1.Syndesis) error
}

// NewOperatorActions gives the default set of actions operator will perform
func NewOperatorActions(mgr manager.Manager, api kubernetes.Interface) []SyndesisOperatorAction {
	return []SyndesisOperatorAction{
		newCheckUpdatesAction(mgr, api),
		newUpgradeAction(mgr, api),
		newUpgradeBackoffAction(mgr, api),
		newInitializeAction(mgr, api),
		newInstallAction(mgr, api),
		newBackupAction(mgr, api),
		newStartupAction(mgr, api),
	}
}

func newBaseAction(mgr manager.Manager, api kubernetes.Interface, typeS string) baseAction {
	return baseAction{
		actionLog.WithValues("type", typeS),
		mgr.GetClient(),
		mgr.GetScheme(),
		api,
		mgr,
	}
}

func syndesisPhaseIs(syndesis *v1beta1.Syndesis, statuses ...v1beta1.SyndesisPhase) bool {
	if syndesis == nil {
		return false
	}

	currentStatus := syndesis.Status.Phase
	for _, status := range statuses {
		if currentStatus == status {
			return true
		}
	}
	return false
}

func createOrReplaceForce(ctx context.Context, client client.Client, res runtime.Object, force bool) error {
	err := client.Create(ctx, res)
	if err == nil {
		return nil
	}

	if !k8serrors.IsAlreadyExists(err) {
		return err
	}

	if force || canResourceBeReplaced(res) {
		if err := client.Delete(ctx, res); err != nil {
			return err
		}
		return client.Create(ctx, res)
	}

	return nil
}

func canResourceBeReplaced(res runtime.Object) bool {
	if !replaceResourcesIfPresent {
		return false
	}

	if _, blacklisted := res.(*corev1.PersistentVolumeClaim); blacklisted {
		return false
	}
	return true
}
